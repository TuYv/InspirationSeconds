package com.example.wxnotion.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.wxnotion.mapper.TaskDraftMapper;
import com.example.wxnotion.model.ConversationEntry;
import com.example.wxnotion.model.TaskDetectionResult;
import com.example.wxnotion.model.TaskDraft;
import com.example.wxnotion.model.TaskDraftData;
import com.example.wxnotion.model.TaskMatchResult;
import com.example.wxnotion.model.UserConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 任务草稿状态机服务。
 * 管理追问过程中的草稿：创建、匹配回复、填字段、判断完成、超时清理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskDraftService {

    private final TaskDraftMapper taskDraftMapper;
    private final AiService aiService;

    // ────────────────────────── 创建草稿 ──────────────────────────

    /**
     * 根据 AI 检测结果创建草稿，记录初始对话。
     */
    public TaskDraft createDraft(String openId, String originalMessage, TaskDetectionResult result) {
        TaskDraftData data = new TaskDraftData();
        data.setTaskType(result.getTaskType());

        TaskDetectionResult.Extracted ex = result.getExtracted();
        if (ex != null) {
            data.setName(ex.getName());
            data.setCycle(ex.getCycle());
            data.setTrigger(ex.getTrigger());
            data.setCurrentProgress(ex.getCurrentProgress());
            data.setEndCondition(ex.getEndCondition());
        }
        data.setMissingFields(result.getMissingFields() != null
                ? new ArrayList<>(result.getMissingFields())
                : new ArrayList<>());

        List<ConversationEntry> conversationLog = new ArrayList<>();
        conversationLog.add(ConversationEntry.of("user", originalMessage));

        TaskDraft draft = new TaskDraft();
        draft.setOpenId(openId);
        draft.setDraftJson(data);
        draft.setConversationLog(conversationLog);
        draft.setStatus("PENDING");
        draft.setCreatedAt(LocalDateTime.now());
        draft.setUpdatedAt(LocalDateTime.now());

        taskDraftMapper.insert(draft);
        log.info("草稿已创建，用户: {}, draftId: {}", openId, draft.getId());
        return draft;
    }

    // ────────────────────────── 查询 ──────────────────────────

    public List<TaskDraft> findPendingDrafts(String openId) {
        return taskDraftMapper.selectList(new QueryWrapper<TaskDraft>()
                .eq("open_id", openId)
                .eq("status", "PENDING")
                .orderByAsc("created_at"));
    }

    // ────────────────────────── 匹配回复 ──────────────────────────

    /**
     * AI 语义匹配：判断 message 是否回答了某个 PENDING 草稿的追问。
     * @return 匹配到的草稿，若无匹配返回 null
     */
    public TaskDraft matchReplyToDraft(UserConfig user, String message) {
        String openId = user != null ? user.getOpenId() : null;
        List<TaskDraft> pending = findPendingDrafts(openId);
        if (pending.isEmpty()) return null;
        if (pending.size() == 1) {
            // 只有一个草稿，直接用（避免不必要的 AI 调用）
            return pending.get(0);
        }

        // 多个草稿时让 AI 判断
        StringBuilder draftsDesc = new StringBuilder();
        for (TaskDraft d : pending) {
            draftsDesc.append("草稿ID=").append(d.getId())
                    .append(", 任务名=").append(d.getDraftJson().getName())
                    .append(", 待收集=").append(d.getDraftJson().getMissingFields())
                    .append("\n");
        }

        String userContent = "用户消息：" + message + "\n\n待确认草稿：\n" + draftsDesc;
        try {
            TaskMatchResult result = aiService.chatForObject(user, AiPrompts.TASK_DRAFT_MATCH_PROMPT, userContent, TaskMatchResult.class);
            if (result.matchedDraftId == null) return null;
            return pending.stream().filter(d -> d.getId().equals(result.matchedDraftId)).findFirst().orElse(null);
        } catch (Exception e) {
            log.warn("草稿匹配 AI 响应解析失败，用户: {}", user != null ? user.getOpenId() : "null");
            return null;
        }
    }

    // ────────────────────────── 填字段 ──────────────────────────

    /**
     * 将用户回复填入草稿对应字段，更新 conversation_log。
     */
    public void applyReplyToDraft(TaskDraft draft, String field, String value, String userMessage, String assistantReply) {
        TaskDraftData data = draft.getDraftJson();

        switch (field) {
            case "name"             -> data.setName(value);
            case "cycle"            -> data.setCycle(value);
            case "trigger"          -> data.setTrigger(value);
            case "current_progress" -> data.setCurrentProgress(value);
            case "end_condition"    -> data.setEndCondition(value);
        }
        data.getMissingFields().remove(field);

        List<ConversationEntry> log = draft.getConversationLog();
        log.add(ConversationEntry.of("user", userMessage));
        if (assistantReply != null) {
            log.add(ConversationEntry.of("assistant", assistantReply));
        }

        draft.setDraftJson(data);
        draft.setConversationLog(log);
        draft.setUpdatedAt(LocalDateTime.now());

        taskDraftMapper.updateById(draft);
    }

    /**
     * 追加系统回复到对话历史（不填字段，仅记录对话）。
     */
    public void appendToConversation(TaskDraft draft, String role, String content) {
        List<ConversationEntry> log = draft.getConversationLog();
        log.add(ConversationEntry.of(role, content));
        draft.setConversationLog(log);
        draft.setUpdatedAt(LocalDateTime.now());
        taskDraftMapper.updateById(draft);
    }

    // ────────────────────────── 追问生成 ──────────────────────────

    /**
     * AI 生成下一个追问话术。
     */
    public String generateClarificationQuestion(UserConfig user, TaskDraft draft) {
        List<String> missing = draft.getDraftJson().getMissingFields();
        if (missing.isEmpty()) return null;

        String nextField = missing.get(0);
        String fieldLabel = switch (nextField) {
            case "name"             -> "任务名称";
            case "cycle"            -> "重复周期（如每天、每周一）";
            case "trigger"          -> "触发这个任务的背景或原因";
            case "current_progress" -> "当前进度（如已完成了多少）";
            case "end_condition"    -> "完成标准（什么情况下算完成了）";
            default                 -> nextField;
        };

        TaskDraftData data = draft.getDraftJson();
        String userContent = "任务草稿：" + data.getName()
                + "（" + data.getTaskType() + "）\n"
                + "需要询问：" + fieldLabel;

        return aiService.chat(user, AiPrompts.TASK_DRAFT_CLARIFY_PROMPT, userContent);
    }

    // ────────────────────────── 完成判断 ──────────────────────────

    /**
     * 判断草稿字段是否完整，可以创建任务。
     */
    public boolean isDraftComplete(TaskDraft draft) {
        TaskDraftData data = draft.getDraftJson();
        return data.getMissingFields().isEmpty() && data.getName() != null;
    }

    /**
     * 删除草稿（任务创建完成后调用）。
     */
    public void deleteDraft(Long draftId) {
        taskDraftMapper.deleteById(draftId);
    }

    // ────────────────────────── 超时清理 ──────────────────────────

    /**
     * 每天凌晨2点清理超过7天未更新的 PENDING 草稿。
     */
    @Scheduled(cron = "0 0 2 * * ?", zone = "Asia/Shanghai")
    public void expireOldDrafts() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(7);
        int count = taskDraftMapper.update(null, new UpdateWrapper<TaskDraft>()
                .eq("status", "PENDING")
                .lt("updated_at", threshold)
                .set("status", "EXPIRED"));
        if (count > 0) {
            log.info("已将 {} 个超时草稿标记为 EXPIRED", count);
        }
    }
}
