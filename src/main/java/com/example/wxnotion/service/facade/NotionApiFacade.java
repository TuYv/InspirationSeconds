package com.example.wxnotion.service.facade;

import com.example.wxnotion.config.NotionProperties;
import com.example.wxnotion.http.HttpClient;
import com.example.wxnotion.http.HttpClient.HttpResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotionApiFacade {

  private final HttpClient httpClient;
  private final NotionProperties notionProps;
  private final ObjectMapper mapper;

  public String createGuestDatabase(String token, String parentPageId, String title) {
    return runWithRetry("createGuestDatabase", () -> {
      Map<String, Object> payload = new HashMap<>();
      payload.put("parent", Map.of("type", "page_id", "page_id", parentPageId));
      payload.put("title", Collections.singletonList(Map.of(
          "type", "text",
          "text", Map.of("content", title)
      )));

      Map<String, Object> properties = new LinkedHashMap<>();
      properties.put("Name", Map.of("title", Map.of()));
      properties.put("Created time", Map.of("created_time", Map.of()));
      properties.put("Date", Map.of("date", Map.of()));
      properties.put("Description", Map.of("rich_text", Map.of()));
      properties.put("Last edited time", Map.of("last_edited_time", Map.of()));
      properties.put("Status", Map.of("status", Map.of()));
      properties.put("Title", Map.of("rich_text", Map.of()));
      payload.put("properties", properties);

      String json = mapper.writeValueAsString(payload);
      HttpResponse resp = httpClient.execute(new HttpClient.HttpRequest(
          "https://api.notion.com/v1/databases",
          "POST",
          json,
          buildHeaders(token)
      ));
      if (!resp.isSuccessful) {
        throw translateHttp("createGuestDatabase", resp, "parentPageId=" + parentPageId);
      }
      JsonNode root = mapper.readTree(resp.body);
      return root.path("id").asText(null);
    });
  }

  /**
   * 创建任务专用 Database（独立于 Notes Database）。
   * Notion API 创建数据库时可能忽略 properties，因此先创建再 PATCH 补属性。
   */
  public String createTasksDatabase(String token, String parentPageId, String title) {
    return runWithRetry("createTasksDatabase", () -> {
      // Step 1: 创建数据库（只带标题）
      Map<String, Object> payload = new HashMap<>();
      payload.put("parent", Map.of("type", "page_id", "page_id", parentPageId));
      payload.put("title", Collections.singletonList(Map.of(
          "type", "text",
          "text", Map.of("content", title)
      )));
      payload.put("properties", Map.of("Name", Map.of("title", Map.of())));

      String json = mapper.writeValueAsString(payload);
      HttpResponse resp = httpClient.execute(new HttpClient.HttpRequest(
          "https://api.notion.com/v1/databases",
          "POST",
          json,
          buildHeaders(token)
      ));
      if (!resp.isSuccessful) {
        throw translateHttp("createTasksDatabase", resp, "parentPageId=" + parentPageId);
      }
      JsonNode root = mapper.readTree(resp.body);
      String dbId = root.path("id").asText(null);

      // Step 2: PATCH 补上所有自定义属性
      patchTasksDatabaseSchema(token, dbId);

      return dbId;
    });
  }

  /**
   * 向 Tasks Database PATCH 添加任务所需的自定义属性列。
   */
  public void patchTasksDatabaseSchema(String token, String dbId) {
    try {
      Map<String, Object> properties = new LinkedHashMap<>();
      properties.put("Type", Map.of("select", Map.of("options", List.of(
          Map.of("name", "recurring", "color", "blue"),
          Map.of("name", "one_time", "color", "green")
      ))));
      properties.put("Cycle", Map.of("rich_text", Map.of()));
      properties.put("Trigger", Map.of("rich_text", Map.of()));
      properties.put("Progress", Map.of("rich_text", Map.of()));
      properties.put("EndCondition", Map.of("rich_text", Map.of()));
      properties.put("Status", Map.of("select", Map.of("options", List.of(
          Map.of("name", "active", "color", "green"),
          Map.of("name", "completed", "color", "blue"),
          Map.of("name", "abandoned", "color", "yellow"),
          Map.of("name", "deleted", "color", "gray")
      ))));
      properties.put("CreatedAt", Map.of("date", Map.of()));
      properties.put("CronExpr", Map.of("rich_text", Map.of()));

      String patchJson = mapper.writeValueAsString(Map.of("properties", properties));
      HttpResponse patchResp = httpClient.execute(new HttpClient.HttpRequest(
          "https://api.notion.com/v1/databases/" + dbId,
          "PATCH",
          patchJson,
          buildHeaders(token)
      ));
      if (!patchResp.isSuccessful) {
        log.warn("Tasks Database schema PATCH 失败，dbId={}, code={}, body={}",
            dbId, patchResp.code, patchResp.body);
      } else {
        log.info("Tasks Database schema 已补全，dbId={}", dbId);
      }
    } catch (Exception e) {
      log.error("Tasks Database schema PATCH 异常，dbId={}", dbId, e);
    }
  }

  /**
   * 创建 Notion 页面，payload 为已序列化的 JSON 字符串
   */
  public HttpResponse createPage(String token, String payloadJson) {
    return runWithRetry("createPage", () -> {
      HttpResponse resp = httpClient.execute(new HttpClient.HttpRequest(
          "https://api.notion.com/v1/pages",
          "POST",
          payloadJson,
          buildHeaders(token)
      ));
      if (!resp.isSuccessful) {
        throw translateHttp("createPage", resp, null);
      }
      return resp;
    });
  }

  /**
   * 向 Notion 页面追加 blocks
   */
  public void appendBlockChildren(String token, String pageId, List<Map<String, Object>> blocks) {
    runWithRetry("appendBlockChildren", () -> {
      Map<String, Object> payload = Map.of("children", blocks);
      String json = mapper.writeValueAsString(payload);
      HttpResponse resp = httpClient.execute(new HttpClient.HttpRequest(
          "https://api.notion.com/v1/blocks/" + pageId + "/children",
          "PATCH",
          json,
          buildHeaders(token)
      ));
      if (!resp.isSuccessful) {
        throw translateHttp("appendBlockChildren", resp, "pageId=" + pageId);
      }
      return null;
    });
  }

  /**
   * 更新页面的任意 Properties（PATCH /pages/{pageId}）
   */
  public void patchPageProperties(String token, String pageId, Map<String, Object> properties) {
    runWithRetry("patchPageProperties", () -> {
      Map<String, Object> payload = Map.of("properties", properties);
      String json = mapper.writeValueAsString(payload);
      HttpResponse resp = httpClient.execute(new HttpClient.HttpRequest(
          "https://api.notion.com/v1/pages/" + pageId,
          "PATCH",
          json,
          buildHeaders(token)
      ));
      if (!resp.isSuccessful) {
        throw translateHttp("patchPageProperties", resp, "pageId=" + pageId);
      }
      return null;
    });
  }

  /**
   * 获取 Database 实际存在的属性名集合（GET /databases/{databaseId}，取 properties 的 key）
   * 用于在创建页面前校验属性是否真实存在
   */
  public Set<String> getDatabasePropertyNames(String token, String databaseId) {
    return runWithRetry("getDatabasePropertyNames", () -> {
      HttpResponse resp = httpClient.execute(new HttpClient.HttpRequest(
          "https://api.notion.com/v1/databases/" + databaseId,
          "GET",
          null,
          buildHeaders(token)
      ));
      if (!resp.isSuccessful) {
        log.warn("获取数据库属性失败，databaseId={}, code={}", databaseId, resp.code);
        return new HashSet<>();
      }
      JsonNode root = mapper.readTree(resp.body);
      JsonNode properties = root.path("properties");
      Set<String> names = new HashSet<>();
      properties.fieldNames().forEachRemaining(names::add);
      return names;
    });
  }

  /**
   * 获取 Database 的父页面 ID（GET /databases/{databaseId}，取 parent.page_id）
   * 用于在同级创建 Tasks Database
   */
  public String getDatabaseParentPageId(String token, String databaseId) {
    return runWithRetry("getDatabaseParentPageId", () -> {
      HttpResponse resp = httpClient.execute(new HttpClient.HttpRequest(
          "https://api.notion.com/v1/databases/" + databaseId,
          "GET",
          null,
          buildHeaders(token)
      ));
      if (!resp.isSuccessful) {
        throw translateHttp("getDatabaseParentPageId", resp, "databaseId=" + databaseId);
      }
      JsonNode root = mapper.readTree(resp.body);
      JsonNode parent = root.path("parent");
      String pageId = parent.path("page_id").asText(null);
      if (pageId == null) {
        throw new NotionApiException("invalid_parent", 400,
            "Database parent is not a page: " + parent.path("type").asText(), "databaseId=" + databaseId);
      }
      return pageId;
    });
  }

  /**
   * 读取页面属性（GET /pages/{pageId}）
   */
  public JsonNode getPage(String token, String pageId) {
    return runWithRetry("getPage", () -> {
      HttpResponse resp = httpClient.execute(new HttpClient.HttpRequest(
          "https://api.notion.com/v1/pages/" + pageId,
          "GET",
          null,
          buildHeaders(token)
      ));
      if (!resp.isSuccessful) {
        throw translateHttp("getPage", resp, "pageId=" + pageId);
      }
      return mapper.readTree(resp.body);
    });
  }

  public QueryResult queryDatabase(String token, String anyDatabaseId, String cursor, int pageSize) {
    return runWithRetry("queryDatabase", () -> queryDatabaseOnce(token, anyDatabaseId, cursor, pageSize));
  }

  private QueryResult queryDatabaseOnce(String token, String anyDatabaseId, String cursor, int pageSize) throws IOException {
    String dataSourceId = resolveDataSourceId(token, anyDatabaseId);

    Map<String, Object> body = new HashMap<>();
    body.put("page_size", pageSize);
    if (cursor != null) body.put("start_cursor", cursor);
    Map<String, String> sort = new HashMap<>();
    sort.put("timestamp", "created_time");
    sort.put("direction", "ascending");
    body.put("sorts", Collections.singletonList(sort));

    String json = mapper.writeValueAsString(body);
    String url = "https://api.notion.com/v1/data_sources/" + dataSourceId + "/query";

    HttpResponse resp = httpClient.execute(new HttpClient.HttpRequest(url, "POST", json, buildHeaders(token)));
    if (!resp.isSuccessful && resp.code == 404) {
      String fallbackUrl = "https://api.notion.com/v1/databases/" + dataSourceId + "/query";
      resp = httpClient.execute(new HttpClient.HttpRequest(fallbackUrl, "POST", json, buildHeaders(token)));
    }

    if (!resp.isSuccessful) {
      NotionApiException ne = translateHttp("queryDatabase", resp, "id=" + anyDatabaseId + ",dataSourceId=" + dataSourceId);
      throw ne;
    }

    JsonNode root = mapper.readTree(resp.body);
    JsonNode results = root.path("results");
    String nextCursor = root.path("next_cursor").asText(null);
    boolean hasMore = root.path("has_more").asBoolean(false);
    return new QueryResult(results, nextCursor, hasMore);
  }

  private String resolveDataSourceId(String token, String databaseId) throws IOException {
    HttpResponse resp = httpClient.execute(new HttpClient.HttpRequest(
        "https://api.notion.com/v1/databases/" + databaseId,
        "GET",
        null,
        buildHeaders(token)
    ));

    if (!resp.isSuccessful) {
      return databaseId;
    }

    JsonNode root = mapper.readTree(resp.body);
    JsonNode dataSources = root.path("data_sources");
    if (dataSources.isArray() && dataSources.size() > 0) {
      return dataSources.get(0).path("id").asText(databaseId);
    }
    return databaseId;
  }

  private <T> T runWithRetry(String name, SupplierWithException<T> s) {
    int attempt = 0;
    long delay = 300L;
    while (true) {
      try {
        T res = s.get();
        if (attempt > 0) {
          log.info("Notion op {} succeeded after retry {}", name, attempt);
        }
        return res;
      } catch (NotionApiException e) {
        if (e.getHttpStatus() == 429 || (e.getHttpStatus() >= 500 && e.getHttpStatus() < 600)) {
          if (attempt >= 3) throw e;
          sleep(delay);
          delay = Math.min(delay * 2, 2000L);
          attempt++;
          continue;
        }
        throw e;
      } catch (Exception e) {
        NotionApiException ne = translate(name, e, null);
        if (ne.getHttpStatus() == 429 || (ne.getHttpStatus() >= 500 && ne.getHttpStatus() < 600)) {
          if (attempt >= 3) throw ne;
          sleep(delay);
          delay = Math.min(delay * 2, 2000L);
          attempt++;
          continue;
        }
        throw ne;
      }
    }
  }

  private void sleep(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException ignored) {
    }
  }

  private NotionApiException translate(String name, Exception e, String ctx) {
    String msg = e.getMessage() != null ? e.getMessage() : "";
    if (msg.contains("unauthorized")) {
      return new NotionApiException("unauthorized", 401, msg, ctx != null ? ctx : name);
    }
    if (msg.contains("validation_error")) {
      return new NotionApiException("validation_error", 400, msg, ctx != null ? ctx : name);
    }
    if (msg.contains("object_not_found")) {
      return new NotionApiException("object_not_found", 404, msg, ctx != null ? ctx : name);
    }
    if (msg.contains("invalid_request_url")) {
      return new NotionApiException("invalid_request_url", 400, msg, ctx != null ? ctx : name);
    }
    return new NotionApiException("unknown_error", 500, msg, ctx != null ? ctx : name);
  }

  private NotionApiException translateHttp(String name, HttpResponse resp, String ctx) {
    String msg = resp.body != null ? resp.body : "";
    String code = "unknown_error";
    int status = resp.code;
    try {
      JsonNode node = mapper.readTree(msg);
      code = node.path("code").asText(code);
      msg = node.path("message").asText(msg);
    } catch (Exception ignored) {
    }
    return new NotionApiException(code, status, msg, ctx != null ? ctx : name);
  }

  private Map<String, String> buildHeaders(String token) {
    Map<String, String> headers = new HashMap<>();
    headers.put("Authorization", "Bearer " + token);
    headers.put("Notion-Version", notionProps.getVersion() != null ? notionProps.getVersion() : "2022-06-28");
    headers.put("Content-Type", "application/json");
    return headers;
  }

  public record QueryResult(JsonNode results, String nextCursor, boolean hasMore) {}

  @FunctionalInterface
  private interface SupplierWithException<T> {
    T get() throws Exception;
  }
}
