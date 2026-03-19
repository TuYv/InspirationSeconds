package com.example.wxnotion.service;

import com.example.wxnotion.model.ConversationEntry;
import com.example.wxnotion.model.TaskTerminationResult;
import com.example.wxnotion.model.UserConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 任务生命周期服务：进度更新 + 任务终结。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskLifecycleService {

    private final TaskNotionService taskNotionService;
    private final TaskReminderService taskReminderService;
    private final AiService aiService;

    /**
     * 将用户消息追加为任务进度，更新 Progress 属性。
     * @return 回复给用户的文本
     */
    public String updateProgress(UserConfig userConfig, String taskPageId, String taskName, String message) {
        try {
            // 追加到对话历史
            List<ConversationEntry> entries = List.of(
                    ConversationEntry.of("user", message),
                    ConversationEntry.of("assistant", "✅ 进度已记录：" + taskName)
            );
            taskNotionService.appendConversationHistory(userConfig, taskPageId, entries);

            // 更新 Progress 属性
            taskNotionService.updateTaskProgress(userConfig, taskPageId, message);

            return "✅ 进度已记录：" + taskName;
        } catch (Exception e) {
            log.error("更新任务进度失败，pageId: {}", taskPageId, e);
            return "记录进度时出了点问题，请稍后重试。";
        }
    }

    /**
     * AI 识别终结意图。
     */
    public TaskTerminationResult detectTerminationIntent(UserConfig userConfig, String message, String activeTaskSummary) {
        String userContent = "用户消息：" + message
                + "\n\n当前 active 任务：" + (activeTaskSummary != null ? activeTaskSummary : "[]");

        try {
            TaskTerminationResult result = aiService.chatForObject(
                    userConfig, AiPrompts.TASK_TERMINATION_PROMPT, userContent, TaskTerminationResult.class);
            result.status = nullIfEmpty(result.status);
            result.taskPageId = nullIfEmpty(result.taskPageId);
            result.taskName = nullIfEmpty(result.taskName);
            return result;
        } catch (Exception e) {
            log.warn("终结意图解析失败，用户: {}: {}", userConfig != null ? userConfig.getOpenId() : "null", e.getMessage());
            return new TaskTerminationResult();
        }
    }

    /**
     * 执行任务终结：更新 Notion Status，追加终结记录。
     * @return 回复给用户的文本
     */
    public String executeTermination(UserConfig userConfig, String taskPageId, String taskName, String status) {
        try {
            taskNotionService.updateTaskStatus(userConfig, taskPageId, status);
            taskReminderService.cancelReminder(taskPageId);

            String marker = switch (status) {
                case "completed" -> "🎉 任务已完成";
                case "abandoned" -> "🏳️ 任务已放弃";
                default -> "🗑️ 任务已删除";
            };
            taskNotionService.appendConversationHistory(userConfig, taskPageId,
                    List.of(ConversationEntry.of("assistant", marker)));

            return switch (status) {
                case "completed" -> "✅ 任务「" + taskName + "」已完成，恭喜！";
                case "abandoned" -> "好的，任务「" + taskName + "」已放弃。";
                default -> "已删除任务「" + taskName + "」。";
            };
        } catch (Exception e) {
            log.error("执行任务终结失败，pageId: {}", taskPageId, e);
            return "操作失败，请稍后重试。";
        }
    }

    private String nullIfEmpty(String s) {
        if (s == null || s.isBlank() || "null".equalsIgnoreCase(s)) return null;
        return s;
    }
}
