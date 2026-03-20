package com.example.wxnotion.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.wxnotion.config.NotionProperties;
import com.example.wxnotion.mapper.UserConfigRepository;
import com.example.wxnotion.model.ConversationEntry;
import com.example.wxnotion.model.TaskDraft;
import com.example.wxnotion.model.TaskDraftData;
import com.example.wxnotion.model.TaskSummary;
import com.example.wxnotion.model.UserConfig;
import com.example.wxnotion.http.HttpClient;
import com.example.wxnotion.service.facade.NotionApiFacade;
import com.example.wxnotion.util.AesUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * 负责在 Notion 中管理任务相关数据：
 * - 懒初始化用户的 Tasks Database
 * - 创建任务页面（写入 Properties）
 * - 追加对话历史为 blocks
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskNotionService {

    private final NotionApiFacade notionApiFacade;
    private final NotionProperties notionProperties;
    private final UserConfigRepository userConfigRepository;
    private final ObjectMapper objectMapper;

    @Value("${security.aesKey}")
    private String aesKey;

    /**
     * 确保用户的 Tasks Database 存在，若不存在则创建并回写 ID。
     * @return Tasks Database ID
     */
    public String ensureTasksDatabase(UserConfig userConfig) {
        if (userConfig.getTasksDatabaseId() != null && !userConfig.getTasksDatabaseId().isBlank()) {
            // 已有数据库 ID，检查属性是否完整（修复历史上属性未创建的情况）
            String token = resolveToken(userConfig);
            String dbId = userConfig.getTasksDatabaseId();
            Set<String> existing = notionApiFacade.getDatabasePropertyNames(token, dbId);
            if (!existing.contains("Status") || !existing.contains("CronExpr")) {
                log.warn("Tasks Database {} 属性不完整（{}），尝试补全", dbId, existing);
                notionApiFacade.patchTasksDatabaseSchema(token, dbId);
            }
            return dbId;
        }

        String token = resolveToken(userConfig);
        String parentPageId = resolveParentPageId(userConfig);
        if (parentPageId == null) {
            throw new IllegalStateException("无法确定 Tasks Database 的父页面 ID，用户: " + userConfig.getOpenId());
        }

        String dbId = notionApiFacade.createTasksDatabase(token, parentPageId, "任务追踪");
        if (dbId == null) {
            throw new RuntimeException("创建 Tasks Database 失败，用户: " + userConfig.getOpenId());
        }

        userConfig.setTasksDatabaseId(dbId);
        userConfigRepository.update(null, new UpdateWrapper<UserConfig>()
                .eq("open_id", userConfig.getOpenId())
                .set("tasks_database_id", dbId));

        log.info("用户 {} Tasks Database 已创建: {}", userConfig.getOpenId(), dbId);
        return dbId;
    }

    /**
     * 在 Tasks Database 中创建任务页面，返回新页面 ID。
     */
    public String createTaskPage(UserConfig userConfig, TaskDraft draft) {
        String token = resolveToken(userConfig);
        String dbId = ensureTasksDatabase(userConfig);

        TaskDraftData draftData = draft.getDraftJson();
        String taskType = draftData.getTaskType() != null ? draftData.getTaskType() : "one_time";

        Map<String, Object> props = new LinkedHashMap<>();

        // Name（title 属性）
        String name = draftData.getName() != null ? draftData.getName() : "未命名任务";
        props.put("Name", Map.of("title", List.of(
                Map.of("type", "text", "text", Map.of("content", name))
        )));

        // Type
        props.put("Type", Map.of("select", Map.of("name", taskType)));

        // Cycle（周期任务）
        String cycle = draftData.getCycle();
        if (cycle != null && !cycle.isBlank()) {
            props.put("Cycle", Map.of("rich_text", List.of(
                    Map.of("type", "text", "text", Map.of("content", cycle))
            )));
        }

        // Trigger（一次性任务）
        String trigger = draftData.getTrigger();
        if (trigger != null && !trigger.isBlank()) {
            props.put("Trigger", Map.of("rich_text", List.of(
                    Map.of("type", "text", "text", Map.of("content", trigger))
            )));
        }

        // Progress
        String progress = draftData.getCurrentProgress();
        if (progress != null && !progress.isBlank()) {
            props.put("Progress", Map.of("rich_text", List.of(
                    Map.of("type", "text", "text", Map.of("content", progress))
            )));
        }

        // EndCondition
        String endCondition = draftData.getEndCondition();
        if (endCondition != null && !endCondition.isBlank()) {
            props.put("EndCondition", Map.of("rich_text", List.of(
                    Map.of("type", "text", "text", Map.of("content", endCondition))
            )));
        }

        // Status
        props.put("Status", Map.of("select", Map.of("name", "active")));

        // CreatedAt
        String today = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
        props.put("CreatedAt", Map.of("date", Map.of("start", today)));

        // 验证数据库实际存在的属性，过滤掉不存在的（防止数据库刚创建时属性未生效）
        Set<String> actualProps = notionApiFacade.getDatabasePropertyNames(token, dbId);
        if (!actualProps.isEmpty()) {
            props.entrySet().removeIf(e -> !actualProps.contains(e.getKey()));
            if (props.size() <= 1) {
                // 只剩 Name，说明数据库属性未创建成功，记录告警
                log.warn("Tasks Database {} 属性可能未创建成功，实际属性: {}", dbId, actualProps);
            }
        }

        // 调用 Notion API 创建页面
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("parent", Map.of("database_id", dbId));
        payload.put("properties", props);

        try {
            String json = objectMapper.writeValueAsString(payload);
            HttpClient.HttpResponse resp = notionApiFacade.createPage(token, json);
            JsonNode root = objectMapper.readTree(resp.body);
            String pageId = root.path("id").asText(null);
            log.info("任务页面已创建，用户: {}, pageId: {}", userConfig.getOpenId(), pageId);
            return pageId;
        } catch (Exception e) {
            throw new RuntimeException("创建任务页面失败: " + e.getMessage(), e);
        }
    }

    /**
     * 将对话历史追加到任务页面作为 blocks。
     */
    public void appendConversationHistory(UserConfig userConfig, String pageId,
                                          List<ConversationEntry> conversationLog) {
        if (conversationLog == null || conversationLog.isEmpty()) return;

        String token = resolveToken(userConfig);
        List<Map<String, Object>> blocks = new ArrayList<>();

        for (ConversationEntry entry : conversationLog) {
            String role = entry.getRole() != null ? entry.getRole() : "user";
            String content = entry.getContent() != null ? entry.getContent() : "";
            String timestamp = entry.getTimestamp() != null ? entry.getTimestamp() : "";

            String prefix = "user".equals(role) ? "🧑" : "🤖";
            String text = prefix + " " + timestamp + "  " + content;

            blocks.add(Map.of(
                    "object", "block",
                    "type", "paragraph",
                    "paragraph", Map.of(
                            "rich_text", List.of(Map.of(
                                    "type", "text",
                                    "text", Map.of("content", text)
                            ))
                    )
            ));
        }

        notionApiFacade.appendBlockChildren(token, pageId, blocks);
        log.info("对话历史已追加，pageId: {}, 条数: {}", pageId, conversationLog.size());
    }

    /**
     * 获取用户所有 active 任务的摘要 JSON 字符串，用于传给 AI 检测。
     * 若没有 Tasks Database 则返回 "[]"。
     */
    public String getActiveTaskSummary(UserConfig userConfig) {
        if (userConfig.getTasksDatabaseId() == null || userConfig.getTasksDatabaseId().isBlank()) {
            return "[]";
        }
        try {
            String token = resolveToken(userConfig);
            var result = notionApiFacade.queryDatabase(token, userConfig.getTasksDatabaseId(), null, 50);
            if (result == null || result.results() == null) return "[]";

            List<TaskSummary> summaries = new ArrayList<>();
            for (var page : result.results()) {
                String pageId = page.path("id").asText(null);
                var props = page.path("properties");
                String status = props.path("Status").path("select").path("name").asText("");
                if (!"active".equals(status)) continue;

                String name = "";
                var titleArr = props.path("Name").path("title");
                if (titleArr.isArray() && !titleArr.isEmpty()) {
                    name = titleArr.get(0).path("plain_text").asText("");
                }
                String type = props.path("Type").path("select").path("name").asText("");

                TaskSummary s = new TaskSummary();
                s.setId(pageId);
                s.setName(name);
                s.setType(type);
                summaries.add(s);
            }
            return objectMapper.writeValueAsString(summaries);
        } catch (Exception e) {
            log.warn("获取 active 任务摘要失败，用户: {}: {}", userConfig.getOpenId(), e.getMessage());
            return "[]";
        }
    }

    // ──────────────────── Status / Progress / CronExpr ────────────────────

    /** 更新任务状态（active/completed/abandoned/deleted） */
    public void updateTaskStatus(UserConfig userConfig, String pageId, String status) {
        String token = resolveToken(userConfig);
        notionApiFacade.patchPageProperties(token, pageId,
                Map.of("Status", Map.of("select", Map.of("name", status))));
        log.info("任务状态已更新，pageId: {}, status: {}", pageId, status);
    }

    /** 更新任务进度文本 */
    public void updateTaskProgress(UserConfig userConfig, String pageId, String progress) {
        String token = resolveToken(userConfig);
        notionApiFacade.patchPageProperties(token, pageId,
                Map.of("Progress", Map.of("rich_text", List.of(
                        Map.of("type", "text", "text", Map.of("content", progress))
                ))));
    }

    /** 写入 cron 表达式到任务页 CronExpr 属性 */
    public void writeCronExpr(UserConfig userConfig, String pageId, String cron) {
        String token = resolveToken(userConfig);
        notionApiFacade.patchPageProperties(token, pageId,
                Map.of("CronExpr", Map.of("rich_text", List.of(
                        Map.of("type", "text", "text", Map.of("content", cron))
                ))));
    }

    /** 读取任务页的 CronExpr 属性 */
    public String getCronExpr(UserConfig userConfig, String pageId) {
        try {
            String token = resolveToken(userConfig);
            JsonNode page = notionApiFacade.getPage(token, pageId);
            JsonNode arr = page.path("properties").path("CronExpr").path("rich_text");
            if (arr.isArray() && !arr.isEmpty()) {
                return arr.get(0).path("plain_text").asText(null);
            }
        } catch (Exception e) {
            log.warn("读取 CronExpr 失败，pageId: {}: {}", pageId, e.getMessage());
        }
        return null;
    }

    private String resolveToken(UserConfig userConfig) {
        if (Boolean.TRUE.equals(userConfig.getIsGuest())) {
            return notionProperties.getAdminToken();
        }
        return AesUtil.decrypt(aesKey, userConfig.getEncryptedApiKey());
    }

    private String resolveParentPageId(UserConfig userConfig) {
        if (Boolean.TRUE.equals(userConfig.getIsGuest())) {
            return notionProperties.getGuestRootPageId();
        }
        // 非访客：查询 Notes Database 的父页面 ID，Tasks DB 将与 Notes DB 同级
        String token = resolveToken(userConfig);
        return notionApiFacade.getDatabaseParentPageId(token, userConfig.getDatabaseId());
    }
}
