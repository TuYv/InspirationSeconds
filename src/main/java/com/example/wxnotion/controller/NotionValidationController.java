package com.example.wxnotion.controller;

import com.example.wxnotion.http.HttpClient;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/notion")
@RequiredArgsConstructor
public class NotionValidationController {

  private static final String NOTION_VERSION = "2022-06-28";
  private static final String NOTION_SEARCH_URL = "https://api.notion.com/v1/search";

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  /**
   * 验证 Notion Integration Token，并返回可访问的数据库列表。
   */
  @PostMapping("/validate-token")
  public ResponseEntity<?> validateToken(@RequestBody @Valid ValidateTokenRequest req) {
    String body = "{\"filter\":{\"value\":\"database\",\"property\":\"object\"}}";
    Map<String, String> headers = Map.of(
        "Authorization", "Bearer " + req.getNotionToken(),
        "Notion-Version", NOTION_VERSION,
        "Content-Type", "application/json"
    );

    try {
      HttpClient.HttpRequest request = new HttpClient.HttpRequest(
          NOTION_SEARCH_URL, "POST", body, headers);
      HttpClient.HttpResponse response = httpClient.execute(request);

      if (response.code == 401 || response.code == 403) {
        return ResponseEntity.badRequest().body(Map.of("error", "invalid_token"));
      }
      if (!response.isSuccessful) {
        return ResponseEntity.badRequest().body(Map.of("error", "notion_error"));
      }

      NotionSearchResponse searchResponse = objectMapper.readValue(
          response.body, NotionSearchResponse.class);
      List<DatabaseInfo> databases = new ArrayList<>();
      if (searchResponse.getResults() != null) {
        for (NotionSearchResponse.NotionObject obj : searchResponse.getResults()) {
          String title = extractTitle(obj);
          databases.add(new DatabaseInfo(obj.getId(), title));
        }
      }
      return ResponseEntity.ok(Map.of("databases", databases));
    } catch (Exception e) {
      log.error("Notion token validation failed", e);
      return ResponseEntity.badRequest().body(Map.of("error", "request_failed"));
    }
  }

  private String extractTitle(NotionSearchResponse.NotionObject obj) {
    try {
      if (obj.getTitle() != null && !obj.getTitle().isEmpty()) {
        String pt = obj.getTitle().get(0).getPlain_text();
        return pt != null ? pt : "(无标题)";
      }
    } catch (Exception ignored) {}
    return "(无标题)";
  }

  @Data
  public static class ValidateTokenRequest {
    @NotBlank
    private String notionToken;
  }

  @Data
  public static class DatabaseInfo {
    private final String id;
    private final String title;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class NotionSearchResponse {
    private List<NotionObject> results;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NotionObject {
      private String id;
      private List<RichText> title;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RichText {
      private String plain_text;
    }
  }
}
