package com.example.wxnotion.service;

import com.example.wxnotion.config.NotionProperties;
import com.example.wxnotion.http.HttpClient;
import com.example.wxnotion.http.HttpClient.HttpResponse;
import com.example.wxnotion.model.notion.NotionBlock;
import com.example.wxnotion.model.notion.NotionPageRequest;
import com.example.wxnotion.model.notion.RichText;
import com.example.wxnotion.util.ContentUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Notion OpenAPI 访问服务。
 *
 * - validate：验证 API Key 与数据库ID是否有效
 * - createPage：在指定数据库下创建页面，标题使用数据库的 title 属性，支持 Markdown 正文转换
 * - findTodayPage：查询今日是否已有页面（按创建时间）
 * - appendContent：向已有页面追加内容块
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotionService {
  private final HttpClient httpClient;
  private final ObjectMapper mapper = new ObjectMapper();
  private final NotionProperties notionProps;

  /**
   * 调用 Notion `GET /v1/databases/{id}` 验证数据库存在与权限有效。
   */
  public boolean validate(String apiKey, String databaseId) {
    try {
      HttpResponse resp = httpClient.execute(new HttpClient.HttpRequest(
          "https://api.notion.com/v1/databases/" + databaseId,
          "GET",
          null,
          buildHeaders(apiKey)
      ));
      
      // 2xx 视为有效，其它状态视为无效
      if (!resp.isSuccessful) {
        log.warn("Notion验证失败: Code={}, Body={}", resp.code, resp.body);
      }
      return resp.isSuccessful;
    } catch (IOException e) {
      log.error("Notion验证请求异常: {}", e.getMessage(), e);
      return false;
    }
  }

  /**
   * 创建页面：动态查找 title 属性名，并构造页面内容。
   *
   * @param content 正文内容，将转换为 Block
   * @return 返回创建结果（包含成功标识与 pageId 或错误原始响应）
   */
  public CreateResult createPage(String apiKey, String databaseId, ContentUtil.NotionContent content) throws IOException {
    String titleProp = findTitleProperty(apiKey, databaseId);
    if (titleProp == null) {
      throw new IOException("No title property in database");
    }

    // Build Properties
    Map<String, Object> props = new HashMap<>();
    Map<String, Object> titleObj = new HashMap<>();
    titleObj.put("title", Collections.singletonList(new RichText(content.getTitle())));
    props.put(titleProp, titleObj);

    // Build Children (Blocks)
    List<NotionBlock> children = new ArrayList<>();
    if (content.getContent() != null && !content.getContent().isEmpty()) {
        children.addAll(parseContentToBlocks(content.getContent()));
    }
    
    // Add tags as a paragraph at the bottom
    if (content.getTags() != null && !content.getTags().isEmpty()) {
        StringBuilder sb = new StringBuilder();
        for (String t : content.getTags()) {
            sb.append("#").append(t).append(" ");
        }
        children.add(NotionBlock.paragraph(sb.toString().trim()));
    }

    NotionPageRequest bodyObj = new NotionPageRequest(databaseId, props, children);
    String json = mapper.writeValueAsString(bodyObj);

    try {
      HttpResponse resp = httpClient.execute(new HttpClient.HttpRequest(
          "https://api.notion.com/v1/pages",
          "POST",
          json,
          buildHeaders(apiKey)
      ));
      
      boolean ok = resp.isSuccessful;
      String pageId = null;
      if (ok) {
        // 解析响应，提取页面ID
        JsonNode node = mapper.readTree(resp.body);
        pageId = node.path("id").asText(null);
      } else {
        log.error("创建Notion页面失败: Code={}, Body={}", resp.code, resp.body);
      }
      return new CreateResult(ok, pageId, resp.body);
    } catch (IOException e) {
      throw e;
    }
  }

  /**
   * 查询数据库中今天创建的第一个页面（按 created_time）。
   * @return Page ID 或 null
   */
  public String findTodayPage(String apiKey, String databaseId) {
    try {
      // 构造查询 Filter：created_time >= 当天 00:00:00 (Asia/Shanghai)
      LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
      String dateStr = today.toString();
      
      String jsonFilter = "{" +
              "\"filter\": {" +
              "  \"timestamp\": \"created_time\"," +
              "  \"created_time\": { \"on_or_after\": \"" + dateStr + "\" }" +
              "}," +
              "\"page_size\": 1" +
              "}";
              
      HttpResponse resp = httpClient.execute(new HttpClient.HttpRequest(
          "https://api.notion.com/v1/databases/" + databaseId + "/query",
          "POST",
          jsonFilter,
          buildHeaders(apiKey)
      ));
      
      if (!resp.isSuccessful) {
        log.warn("查询今日页面失败: Code={}, Body={}", resp.code, resp.body);
        return null;
      }
      
      JsonNode root = mapper.readTree(resp.body);
      JsonNode results = root.path("results");
      if (results.isArray() && results.size() > 0) {
        return results.get(0).path("id").asText();
      }
      return null;
    } catch (IOException e) {
      log.error("查询今日页面异常: {}", e.getMessage(), e);
      return null;
    }
  }

  /**
   * 向指定 Page 追加内容块。
   */
  public boolean appendContent(String apiKey, String pageId, ContentUtil.NotionContent content) {
    try {
      List<NotionBlock> blocks = new ArrayList<>();
      
      // 1. 如果有 Title (作为第一行加粗文本，区分不同条消息)
      if (content.getTitle() != null && !content.getTitle().isEmpty()) {
         blocks.add(NotionBlock.heading(content.getTitle()));
      }
      
      // 2. 正文
      if (content.getContent() != null && !content.getContent().isEmpty()) {
          blocks.addAll(parseContentToBlocks(content.getContent()));
      }
      
      // 3. Tags
      if (content.getTags() != null && !content.getTags().isEmpty()) {
          StringBuilder sb = new StringBuilder();
          for (String t : content.getTags()) {
              sb.append("#").append(t).append(" ");
          }
          blocks.add(NotionBlock.paragraph(sb.toString().trim()));
      }
      
      if (blocks.isEmpty()) return true;

      Map<String, Object> bodyMap = new HashMap<>();
      bodyMap.put("children", blocks);
      String json = mapper.writeValueAsString(bodyMap);
      
      HttpResponse resp = httpClient.execute(new HttpClient.HttpRequest(
          "https://api.notion.com/v1/blocks/" + pageId + "/children",
          "PATCH",
          json,
          buildHeaders(apiKey)
      ));
      
      return resp.isSuccessful;
    } catch (IOException e) {
      log.error("追加页面内容失败: {}", e.getMessage(), e);
      return false;
    }
  }
  
  // ... (parseContentToBlocks 保持不变)

  /**
   * 读取数据库属性，找到类型为 title 的属性名。
   * 兼容新版 API (2025-09-03) 多源数据库结构：
   * 1. 若 GET /databases/{id} 返回 properties，直接解析（兼容旧结构或 Data Source ID）。
   * 2. 若返回 data_sources 列表，提取第一个 Data Source ID 递归查询。
   */
  protected String findTitleProperty(String apiKey, String databaseId) {
    try {
      HttpResponse resp = httpClient.execute(new HttpClient.HttpRequest(
          "https://api.notion.com/v1/databases/" + databaseId,
          "GET",
          null,
          buildHeaders(apiKey)
      ));

      if (!resp.isSuccessful) {
        log.warn("查询Database信息失败: Code={}, Body={}", resp.code, resp.body);
        return null;
      }
      JsonNode root = mapper.readTree(resp.body);
      
      // 1. 尝试直接从 properties 获取（常规 Data Source）
      JsonNode props = root.path("properties");
      if (props.isObject() && !props.isEmpty()) {
        return findTitleInProperties(props);
      }
      
      // 2. 尝试处理 Container Database（多源数据库），提取第一个 Data Source ID
      JsonNode dataSources = root.path("data_sources");
      if (dataSources.isArray() && dataSources.size() > 0) {
        String dataSourceId = dataSources.get(0).path("id").asText();
        log.info("检测到多源数据库，使用第一个Data Source ID: {}", dataSourceId);
        // 递归调用（注意：这次调用的是 data_sources 端点以获取属性）
        return findTitlePropertyFromDataSource(apiKey, dataSourceId);
      }
      
      log.warn("未找到properties且非多源数据库结构，ID={}", databaseId);
      return null;
    } catch (IOException e) {
      log.error("查询数据库Title属性失败: {}", e.getMessage(), e);
      return null;
    }
  }

  // 辅助方法：从 Data Source ID 获取属性（直接调用 data_sources 端点）
  private String findTitlePropertyFromDataSource(String apiKey, String dataSourceId) {
    try {
      HttpResponse resp = httpClient.execute(new HttpClient.HttpRequest(
          "https://api.notion.com/v1/data_sources/" + dataSourceId,
          "GET",
          null,
          buildHeaders(apiKey)
      ));
      
      if (!resp.isSuccessful) return null;
      JsonNode root = mapper.readTree(resp.body);
      return findTitleInProperties(root.path("properties"));
    } catch (IOException e) {
      log.error("递归查询Data Source失败: {}", e.getMessage(), e);
      return null;
    }
  }

  private Map<String, String> buildHeaders(String apiKey) {
    Map<String, String> headers = new HashMap<>();
    headers.put("Authorization", "Bearer " + apiKey);
    headers.put("Notion-Version", notionProps.getVersion());
    return headers;
  }


  // 辅助方法：在 properties 节点中查找 title 类型
  private String findTitleInProperties(JsonNode props) {
    if (props.isObject()) {
      for (Iterator<Map.Entry<String, JsonNode>> it = props.fields(); it.hasNext(); ) {
        Map.Entry<String, JsonNode> e = it.next();
        String type = e.getValue().path("type").asText();
        if ("title".equals(type)) return e.getKey();
      }
    }
    return null;
  }

  /** 创建页面结果模型 */
  public static class CreateResult {
    public final boolean ok;
    public final String pageId;
    public final String raw;
    public CreateResult(boolean ok, String pageId, String raw) {
      this.ok = ok;
      this.pageId = pageId;
      this.raw = raw;
    }
  }
}
