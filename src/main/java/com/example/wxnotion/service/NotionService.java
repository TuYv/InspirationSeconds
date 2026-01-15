package com.example.wxnotion.service;

import com.example.wxnotion.config.NotionProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Iterator;

/**
 * Notion OpenAPI 访问服务。
 *
 * - validate：验证 API Key 与数据库ID是否有效
 * - createPage：在指定数据库下创建页面，标题使用数据库的 title 属性，标签附加为段落文本
 */
@Service
public class NotionService {
  private static final Logger log = LoggerFactory.getLogger(NotionService.class);
  private final OkHttpClient client = new OkHttpClient();
  private final ObjectMapper mapper = new ObjectMapper();
  private final NotionProperties notionProps;

  public NotionService(NotionProperties notionProps) {
    this.notionProps = notionProps;
  }

  /**
   * 调用 Notion `GET /v1/databases/{id}` 验证数据库存在与权限有效。
   */
  public boolean validate(String apiKey, String databaseId) {
    Request req = new Request.Builder()
        // 尝试回滚到 databases 端点，兼容旧版 ID
        .url("https://api.notion.com/v1/databases/" + databaseId)
        .header("Authorization", "Bearer " + apiKey)
        .header("Notion-Version", notionProps.getVersion())
        .build();
    try (Response resp = client.newCall(req).execute()) {
      // 2xx 视为有效，其它状态视为无效
      if (!resp.isSuccessful()) {
        log.warn("Notion验证失败: Code={}, Body={}", resp.code(), resp.body() != null ? resp.body().string() : "");
      }
      return resp.isSuccessful();
    } catch (IOException e) {
      log.error("Notion验证请求异常: {}", e.getMessage(), e);
      return false;
    }
  }

  /**
   * 创建页面：动态查找 title 属性名，并构造页面内容。
   *
   * @return 返回创建结果（包含成功标识与 pageId 或错误原始响应）
   */
  public CreateResult createPage(String apiKey, String databaseId, String title, List<String> tags) throws IOException {
    String titleProp = findTitleProperty(apiKey, databaseId);
    if (titleProp == null) {
      throw new IOException("No title property in database");
    }

    String json = buildCreatePageBody(databaseId, titleProp, title, tags);
    Request req = new Request.Builder()
        .url("https://api.notion.com/v1/pages")
        .header("Authorization", "Bearer " + apiKey)
        .header("Notion-Version", notionProps.getVersion())
        .post(RequestBody.create(json, MediaType.parse("application/json")))
        .build();
    try (Response resp = client.newCall(req).execute()) {
      String respBody = resp.body() != null ? resp.body().string() : "";
      boolean ok = resp.isSuccessful();
      String pageId = null;
      if (ok) {
        // 解析响应，提取页面ID
        JsonNode node = mapper.readTree(respBody);
        pageId = node.path("id").asText(null);
      } else {
        log.error("创建Notion页面失败: Code={}, Body={}", resp.code(), respBody);
      }
      return new CreateResult(ok, pageId, respBody);
    }
  }

  /**
   * 读取数据库属性，找到类型为 title 的属性名。
   * 兼容新版 API (2025-09-03) 多源数据库结构：
   * 1. 若 GET /databases/{id} 返回 properties，直接解析（兼容旧结构或 Data Source ID）。
   * 2. 若返回 data_sources 列表，提取第一个 Data Source ID 递归查询。
   */
  protected String findTitleProperty(String apiKey, String databaseId) {
    Request req = new Request.Builder()
        .url("https://api.notion.com/v1/databases/" + databaseId)
        .header("Authorization", "Bearer " + apiKey)
        .header("Notion-Version", notionProps.getVersion())
        .build();
    try (Response resp = client.newCall(req).execute()) {
      String body = resp.body() != null ? resp.body().string() : "";
      if (!resp.isSuccessful()) {
        log.warn("查询Database信息失败: Code={}, Body={}", resp.code(), body);
        return null;
      }
      JsonNode root = mapper.readTree(body);
      
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
    Request req = new Request.Builder()
        .url("https://api.notion.com/v1/data_sources/" + dataSourceId)
        .header("Authorization", "Bearer " + apiKey)
        .header("Notion-Version", notionProps.getVersion())
        .build();
    try (Response resp = client.newCall(req).execute()) {
      String body = resp.body() != null ? resp.body().string() : "";
      if (!resp.isSuccessful()) return null;
      JsonNode root = mapper.readTree(body);
      return findTitleInProperties(root.path("properties"));
    } catch (IOException e) {
      log.error("递归查询Data Source失败: {}", e.getMessage(), e);
      return null;
    }
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
   * 构造创建页面的请求体 JSON。
   *
   * - parent.database_id 指向目标数据库
   * - properties.title 使用传入的标题内容
   * - children 可选：将标签作为段落文本附加（易于在 Notion 中查看）
   */
  private String buildCreatePageBody(String databaseId, String titleProp, String title, List<String> tags) throws IOException {
    StringBuilder sb = new StringBuilder();
    sb.append("{");
    // 即使在 2025-09-03，对于 Database ID 仍需使用 database_id 字段
    sb.append("\"parent\":{\"database_id\":\"").append(databaseId).append("\"},");
    sb.append("\"properties\":{");
    sb.append("\"").append(titleProp).append("\":{\"title\":[{\"text\":{\"content\":\"").append(escape(title)).append("\"}}]}\n");
    sb.append("}");
    if (tags != null && !tags.isEmpty()) {
      // 以 children 方式附加一个段落，其中包含正文与标签文本
      sb.append(",\"children\":[{\"object\":\"block\",\"type\":\"paragraph\",\"paragraph\":{\"rich_text\":[");
      sb.append("{\"type\":\"text\",\"text\":{\"content\":\"").append(escape(title)).append("\"}}");
      for (String t : tags) {
        sb.append(",{")
          .append("\"type\":\"text\",\"text\":{\"content\":\" ")
          .append(escape("#" + t))
          .append("\"}}");
      }
      sb.append("]}}]");
    }
    sb.append("}");
    return sb.toString();
  }

  /**
   * 转义 JSON 字符串中的特殊字符。
   */
  private String escape(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
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
