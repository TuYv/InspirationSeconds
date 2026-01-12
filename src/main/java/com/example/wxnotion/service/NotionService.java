package com.example.wxnotion.service;

import com.example.wxnotion.config.NotionProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
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
        .url("https://api.notion.com/v1/databases/" + databaseId)
        .header("Authorization", "Bearer " + apiKey)
        .header("Notion-Version", notionProps.getVersion())
        .build();
    try (Response resp = client.newCall(req).execute()) {
      // 2xx 视为有效，其它状态视为无效
      return resp.isSuccessful();
    } catch (IOException e) {
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
      }
      return new CreateResult(ok, pageId, respBody);
    }
  }

  /**
   * 读取数据库属性，找到类型为 title 的属性名。
   */
  protected String findTitleProperty(String apiKey, String databaseId) throws IOException {
    Request req = new Request.Builder()
        .url("https://api.notion.com/v1/databases/" + databaseId)
        .header("Authorization", "Bearer " + apiKey)
        .header("Notion-Version", notionProps.getVersion())
        .build();
    try (Response resp = client.newCall(req).execute()) {
      String body = resp.body() != null ? resp.body().string() : "";
      if (!resp.isSuccessful()) return null;
      JsonNode node = mapper.readTree(body).path("properties");
      if (node.isObject()) {
        for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext(); ) {
          Map.Entry<String, JsonNode> e = it.next();
          String type = e.getValue().path("type").asText();
          if ("title".equals(type)) return e.getKey();
        }
      }
      return null;
    }
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
