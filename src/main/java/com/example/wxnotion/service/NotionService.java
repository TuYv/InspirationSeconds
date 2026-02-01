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
   * 获取指定 Block 的子块列表 (Retrieve block children)
   * 用于读取页面内容，供 AI 总结使用
   */
  public JsonNode retrieveBlockChildren(String apiKey, String blockId) {
    try {
      HttpResponse resp = httpClient.execute(new HttpClient.HttpRequest(
          "https://api.notion.com/v1/blocks/" + blockId + "/children?page_size=100",
          "GET",
          null,
          buildHeaders(apiKey)
      ));

      if (!resp.isSuccessful) {
        log.warn("Notion API 读取失败: Code={}, Body={}", resp.code, resp.body);
        return null;
      }

      return mapper.readTree(resp.body);
    } catch (IOException e) {
      log.error("Notion API 请求异常: {}", e.getMessage(), e);
      return null;
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

  private List<NotionBlock> parseContentToBlocks(String content) {
    List<NotionBlock> blocks = new ArrayList<>();
    String[] lines = content.split("\\r?\\n");
    for (String line : lines) {
      if (line.trim().isEmpty()) {
        blocks.add(NotionBlock.paragraph("")); // Empty line
        continue;
      }
      
      String trimmed = line.trim();
      if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
        blocks.add(NotionBlock.bulletedList(trimmed.substring(2)));
      } else if (trimmed.startsWith("[] ")) {
        blocks.add(NotionBlock.toDo(trimmed.substring(3), false));
      } else if (trimmed.startsWith("[ ] ")) {
        blocks.add(NotionBlock.toDo(trimmed.substring(4), false));
      } else if (trimmed.startsWith("[x] ") || trimmed.startsWith("[X] ")) {
        blocks.add(NotionBlock.toDo(trimmed.substring(4), true));
      } else if (trimmed.startsWith("> ")) {
        blocks.add(NotionBlock.quote(trimmed.substring(2)));
      } else if (trimmed.startsWith("# ")) {
        blocks.add(NotionBlock.heading(trimmed.substring(2)));
      } else {
        blocks.add(NotionBlock.paragraph(line)); 
      }
    }
    return blocks;
  }

  /**
   * 查询数据库中指定日期的页面（按 Title 匹配）。
   * @param date 目标日期
   * @return Page ID 或 null
   */
  public String findPageByDate(String apiKey, String databaseId, LocalDate date) {
    try {
      // 1. 适配多源数据库：获取真实的 Data Source ID
      String realDataSourceId = resolveDataSourceId(apiKey, databaseId);
      if (realDataSourceId == null) {
          log.warn("无法解析Data Source ID，跳过查询页面。ID={}", databaseId);
          return null;
      }
      
      // 2. 获取 Title 属性名 (用于构造查询 Filter)
      String titleProp = findTitleProperty(apiKey, databaseId); 
      if (titleProp == null) {
          log.warn("无法找到Title属性名，跳过查询页面");
          return null;
      }

      // 3. 构造查询 Filter：Title 属性等于目标日期字符串
      String dateStr = date.toString(); // "2026-01-18"
      
      String jsonFilter = "{" +
              "\"filter\": {" +
              "  \"property\": \"" + titleProp + "\"," +
              "  \"rich_text\": { \"equals\": \"" + dateStr + "\" }" +
              "}," +
              "\"page_size\": 1" +
              "}";
              
      HttpResponse resp = httpClient.execute(new HttpClient.HttpRequest(
          "https://api.notion.com/v1/data_sources/" + realDataSourceId + "/query",
          "POST",
          jsonFilter,
          buildHeaders(apiKey)
      ));
      
      // 如果用 data_sources 端点失败（可能是旧版兼容性问题），尝试回退到 databases 端点
      if (!resp.isSuccessful && resp.code == 404) {
           log.info("尝试回退到 databases 端点查询: {}", realDataSourceId);
           resp = httpClient.execute(new HttpClient.HttpRequest(
              "https://api.notion.com/v1/databases/" + realDataSourceId + "/query",
              "POST",
              jsonFilter,
              buildHeaders(apiKey)
          ));
      }
      
      if (!resp.isSuccessful) {
        log.warn("查询日期页面失败: Code={}, Body={}", resp.code, resp.body);
        return null;
      }
      
      JsonNode root = mapper.readTree(resp.body);
      JsonNode results = root.path("results");
      if (results.isArray() && results.size() > 0) {
        return results.get(0).path("id").asText();
      }
      return null;
    } catch (IOException e) {
      log.error("查询日期页面异常: {}", e.getMessage(), e);
      return null;
    }
  }

  /**
   * 查询数据库中标题为今天日期的第一个页面。
   * (便捷方法)
   */
  public String findTodayPage(String apiKey, String databaseId) {
      return findPageByDate(apiKey, databaseId, LocalDate.now(ZoneId.of("Asia/Shanghai")));
  }
  
  /**
   * 解析真实的 Data Source ID。
   * 如果传入的是 Container ID，提取其第一个 Data Source ID。
   * 如果传入的是普通 ID，直接返回。
   */
  private String resolveDataSourceId(String apiKey, String databaseId) throws IOException {
      HttpResponse resp = httpClient.execute(new HttpClient.HttpRequest(
          "https://api.notion.com/v1/databases/" + databaseId,
          "GET",
          null,
          buildHeaders(apiKey)
      ));
      
      if (!resp.isSuccessful) {
          // 可能是直接传了 Data Source ID，尝试直接 check data source endpoint?
          // 或者先假定它就是 ID
          return databaseId;
      }
      
      JsonNode root = mapper.readTree(resp.body);
      JsonNode dataSources = root.path("data_sources");
      if (dataSources.isArray() && dataSources.size() > 0) {
          return dataSources.get(0).path("id").asText();
      }
      // 没有 data_sources，说明它本身可能就是 data source (或者旧版 database)
      return databaseId;
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
  
  /**
   * 获取指定 Page 的属性值（假设为 Rich Text 类型）。
   * @param propertyName 属性名 (如 "Description")
   * @return 属性文本内容，若不存在或空则返回空字符串
   */
  public String getPageProperty(String apiKey, String pageId, String propertyName) {
      try {
          // Notion API: GET /v1/pages/{page_id}/properties/{property_id}
          // 但我们需要先知道 property_id 吗？
          // 不一定，可以直接 GET /v1/pages/{page_id} 获取所有属性值。
          
          HttpResponse resp = httpClient.execute(new HttpClient.HttpRequest(
              "https://api.notion.com/v1/pages/" + pageId,
              "GET",
              null,
              buildHeaders(apiKey)
          ));
          
          if (!resp.isSuccessful) return "";
          
          JsonNode root = mapper.readTree(resp.body);
          JsonNode props = root.path("properties");
          JsonNode targetProp = props.path(propertyName);
          
          // 解析 Rich Text
          if (!targetProp.isMissingNode() && "rich_text".equals(targetProp.path("type").asText())) {
              JsonNode richTextArr = targetProp.path("rich_text");
              StringBuilder sb = new StringBuilder();
              if (richTextArr.isArray()) {
                  for (JsonNode node : richTextArr) {
                      sb.append(node.path("plain_text").asText(""));
                  }
              }
              return sb.toString();
          }
          
          return "";
      } catch (IOException e) {
          log.error("获取页面属性失败", e);
          return "";
      }
  }

  /**
   * 更新页面属性（如 Description）。
   * @param propertyName 属性名 (如 "Description")
   * @param textValue 文本内容
   */
  public boolean updatePageProperty(String apiKey, String pageId, String propertyName, String textValue) {
    try {
      // 构造 Update Properties Payload
      // { "properties": { "Description": { "rich_text": [ { "text": { "content": "..." } } ] } } }
      Map<String, Object> props = new HashMap<>();
      Map<String, Object> propValue = new HashMap<>();
      propValue.put("rich_text", Collections.singletonList(new RichText(textValue)));
      props.put(propertyName, propValue);
      
      Map<String, Object> bodyMap = new HashMap<>();
      bodyMap.put("properties", props);
      String json = mapper.writeValueAsString(bodyMap);

      HttpResponse resp = httpClient.execute(new HttpClient.HttpRequest(
          "https://api.notion.com/v1/pages/" + pageId,
          "PATCH",
          json,
          buildHeaders(apiKey)
      ));
      
      if (!resp.isSuccessful) {
        log.warn("更新页面属性失败: Code={}, Body={}", resp.code, resp.body);
      }
      return resp.isSuccessful;
    } catch (IOException e) {
      log.error("更新页面属性异常: {}", e.getMessage(), e);
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

  /**
   * 创建新的数据库 (用于访客模式)
   * @param apiKey Admin Token
   * @param parentPageId 父页面 ID (Guest_Workspace)
   * @param title 数据库标题 (e.g. NoteBox_OpenId)
   * @return 创建成功的数据库 ID，失败返回 null
   */
  public String createDatabase(String apiKey, String parentPageId, String title) {
      try {
          Map<String, Object> payload = new HashMap<>();
          
          // Parent
          Map<String, String> parent = new HashMap<>();
          parent.put("type", "page_id");
          parent.put("page_id", parentPageId);
          payload.put("parent", parent);
          
          // Title
          payload.put("title", Collections.singletonList(new RichText(title)));
          
          // Properties
          Map<String, Object> properties = new LinkedHashMap<>();
          
          // 1. Name (Title)
          properties.put("Name", Map.of("title", Map.of()));
          
          // 2. Created time
          properties.put("Created time", Map.of("created_time", Map.of()));

          // 3. Date
          properties.put("Date", Map.of("date", Map.of()));

          // 4. Description (RichText)
          properties.put("Description", Map.of("rich_text", Map.of()));

          // 5. Last edited time
          properties.put("Last edited time", Map.of("last_edited_time", Map.of()));

          // 6. Status (Status)
          // API 要求: "status": {}  (不能带 options)
          properties.put("Status", Map.of("status", Map.of()));

          // 7. Title (RichText) - Extra text property
          properties.put("Title", Map.of("rich_text", Map.of()));
          
          payload.put("properties", properties);
          
          String json = mapper.writeValueAsString(payload);
          log.info("Create Database Payload: {}", json);
          
          Map<String, String> headers = new HashMap<>();
          headers.put("Authorization", "Bearer " + apiKey);
          headers.put("Notion-Version", "2022-06-28"); // 强制使用已知可用版本
          headers.put("Content-Type", "application/json");
          
          HttpResponse resp = httpClient.execute(new HttpClient.HttpRequest(
              "https://api.notion.com/v1/databases",
              "POST",
              json,
              headers
          ));
          
          if (!resp.isSuccessful) {
              log.error("创建数据库失败: Code={}, Body={}", resp.code, resp.body);
              return null;
          }
          
          JsonNode root = mapper.readTree(resp.body);
          return root.path("id").asText(null);
      } catch (IOException e) {
          log.error("创建数据库异常: {}", e.getMessage(), e);
          return null;
      }
  }

  /**
   * 分页查询数据库所有页面 (用于数据迁移)
   * @param cursor 分页游标 (传 null 为第一页)
   * @return 查询结果 (Results Node + Next Cursor)
   */
  public QueryResult queryDatabase(String apiKey, String databaseId, String cursor) {
      try {
          String realDataSourceId = resolveDataSourceId(apiKey, databaseId);
          Map<String, Object> body = new HashMap<>();
          body.put("page_size", 50); // 每次取 50 条
          if (cursor != null) {
              body.put("start_cursor", cursor);
          }
          // 按创建时间升序，保证迁移顺序
          Map<String, String> sort = new HashMap<>();
          sort.put("timestamp", "created_time");
          sort.put("direction", "ascending");
          body.put("sorts", Collections.singletonList(sort));
          
          String json = mapper.writeValueAsString(body);

          HttpResponse resp = httpClient.execute(new HttpClient.HttpRequest(
              "https://api.notion.com/v1/data_sources/" + realDataSourceId + "/query",
              "POST",
              json,
              buildHeaders(apiKey)
          ));
          
          if (!resp.isSuccessful) {
              log.warn("查询数据库失败: {}", resp.body);
              return null;
          }
          
          JsonNode root = mapper.readTree(resp.body);
          JsonNode results = root.path("results");
          String nextCursor = root.path("next_cursor").asText(null);
          boolean hasMore = root.path("has_more").asBoolean(false);
          
          return new QueryResult(results, nextCursor, hasMore);
      } catch (IOException e) {
          log.error("查询数据库异常", e);
          return null;
      }
  }
  
  /**
   * 更新数据库 (用于归档重命名)
   */
  public boolean updateDatabase(String apiKey, String databaseId, String newTitle) {
      try {
          Map<String, Object> payload = new HashMap<>();
          payload.put("title", Collections.singletonList(new RichText(newTitle)));
          // payload.put("archived", true); // 可选：直接归档
          
          String json = mapper.writeValueAsString(payload);
          
          HttpResponse resp = httpClient.execute(new HttpClient.HttpRequest(
              "https://api.notion.com/v1/databases/" + databaseId,
              "PATCH",
              json,
              buildHeaders(apiKey)
          ));
          
          return resp.isSuccessful;
      } catch (IOException e) {
          log.error("更新数据库失败", e);
          return false;
      }
  }

  @lombok.Data
  @lombok.AllArgsConstructor
  public static class QueryResult {
      private JsonNode results;
      private String nextCursor;
      private boolean hasMore;
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

