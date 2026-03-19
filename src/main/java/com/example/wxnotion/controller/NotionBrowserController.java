package com.example.wxnotion.controller;

import com.example.wxnotion.config.NotionProperties;
import com.example.wxnotion.mapper.UserConfigRepository;
import com.example.wxnotion.model.UserConfig;
import com.example.wxnotion.service.NotionService;
import com.example.wxnotion.util.AesUtil;
import com.example.wxnotion.util.BlockContentParser;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/notion/pages")
@RequiredArgsConstructor
public class NotionBrowserController {

    private final UserConfigRepository userConfigRepository;
    private final NotionService notionService;
    private final NotionProperties notionProperties;

    @Value("${security.aesKey}")
    private String aesKey;

    /**
     * 按月查询页面列表。
     * 返回 [{date, pageId}] 数组，按 date 升序。
     * title 格式为 YYYY-MM-DD，过滤出以 YYYY-MM- 开头的页面。
     */
    @GetMapping
    public ResponseEntity<?> listPages(
            @AuthenticationPrincipal String openId,
            @RequestParam int year,
            @RequestParam int month) {

        UserConfig cfg = getConfig(openId);
        if (cfg == null) return ResponseEntity.status(401).body(Map.of("error", "no_config"));

        String apiKey = resolveApiKey(cfg);
        String databaseId = cfg.getDatabaseId();
        String prefix = String.format("%04d-%02d-", year, month);

        List<PageEntry> pages = new ArrayList<>();
        String cursor = null;
        int pageLimit = 0;
        do {
            if (++pageLimit > 50) {
                log.warn("Notion 查询超过 50 页，强制中止，databaseId={}", databaseId);
                break;
            }
            NotionService.QueryResult result = notionService.queryDatabase(apiKey, databaseId, cursor);
            if (result == null) break;

            JsonNode results = result.getResults();
            if (results != null && results.isArray()) {
                for (JsonNode page : results) {
                    String pageId = page.path("id").asText();
                    String title = extractPageTitle(page);
                    if (title.startsWith(prefix) && title.length() == prefix.length() + 2) {
                        pages.add(new PageEntry(title, pageId));
                    }
                }
            }

            cursor = result.isHasMore() ? result.getNextCursor() : null;
        } while (cursor != null);

        pages.sort(Comparator.comparing(PageEntry::getDate));
        return ResponseEntity.ok(pages);
    }

    /**
     * 返回页面的 block 数量，用于热力图着色。
     * 异常时返回 {blockCount: 0}，不抛错。
     */
    @GetMapping("/{pageId}/block-count")
    public ResponseEntity<Map<String, Integer>> blockCount(
            @AuthenticationPrincipal String openId,
            @PathVariable String pageId) {

        UserConfig cfg = getConfig(openId);
        if (cfg == null) return ResponseEntity.ok(Map.of("blockCount", 0));

        try {
            String apiKey = resolveApiKey(cfg);
            JsonNode blocks = notionService.retrieveBlockChildren(apiKey, pageId);
            int count = 0;
            if (blocks != null && blocks.has("results") && blocks.get("results").isArray()) {
                count = blocks.get("results").size();
            }
            return ResponseEntity.ok(Map.of("blockCount", count));
        } catch (Exception e) {
            log.warn("获取 block-count 失败，返回 0: pageId={}", pageId, e);
            return ResponseEntity.ok(Map.of("blockCount", 0));
        }
    }

    /**
     * 返回页面正文 Markdown + AI 日报（Description 属性）。
     * pageId 无效时返回 404。
     */
    @GetMapping("/{pageId}/content")
    public ResponseEntity<?> pageContent(
            @AuthenticationPrincipal String openId,
            @PathVariable String pageId) {

        UserConfig cfg = getConfig(openId);
        if (cfg == null) return ResponseEntity.status(401).body(Map.of("error", "no_config"));

        String apiKey = resolveApiKey(cfg);

        JsonNode blocks = notionService.retrieveBlockChildren(apiKey, pageId);
        if (blocks == null) {
            return ResponseEntity.notFound().build();
        }

        String markdown = BlockContentParser.toMarkdown(blocks);
        String aiSummary = "";
        try {
            aiSummary = notionService.getPageProperty(apiKey, pageId, "Description");
            if (aiSummary == null) aiSummary = "";
        } catch (Exception e) {
            log.warn("获取 aiSummary 失败: pageId={}", pageId, e);
        }

        return ResponseEntity.ok(Map.of("markdown", markdown, "aiSummary", aiSummary));
    }

    private UserConfig getConfig(String openId) {
        return userConfigRepository.selectByOpenId(openId);
    }

    private String resolveApiKey(UserConfig cfg) {
        if (Boolean.TRUE.equals(cfg.getIsGuest())) {
            return notionProperties.getAdminToken();
        }
        return AesUtil.decrypt(aesKey, cfg.getEncryptedApiKey());
    }

    private String extractPageTitle(JsonNode page) {
        try {
            JsonNode properties = page.path("properties");
            // 遍历 properties，找类型为 title 的属性
            for (JsonNode prop : properties) {
                if ("title".equals(prop.path("type").asText())) {
                    JsonNode titleArray = prop.path("title");
                    if (titleArray.isArray() && titleArray.size() > 0) {
                        return titleArray.get(0).path("plain_text").asText("");
                    }
                }
            }
        } catch (Exception e) {
            log.debug("提取页面标题失败: {}", e.getMessage());
        }
        return "";
    }

    @Data
    public static class PageEntry {
        private final String date;
        private final String pageId;
    }
}
