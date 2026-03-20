package com.example.wxnotion.service;

import com.example.wxnotion.model.TaskDetectionResult;
import com.example.wxnotion.model.UserConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 任务检测服务。
 * 判断用户消息是否为任务，分类并提取结构化字段。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskDetectionService {

    private final AiService aiService;
    private final ObjectMapper objectMapper;


    /**
     * 检测消息是否为任务，返回结构化结果。
     *
     * @param message           用户消息
     * @param activeTaskSummary 用户当前 active 任务摘要（JSON 字符串，可为空字符串）
     */
    public TaskDetectionResult detectTask(UserConfig user, String message, String activeTaskSummary) {
        String userContent = "【用户消息】\n" + message
                + "\n\n【已有任务列表】\n" + (activeTaskSummary != null ? activeTaskSummary : "[]");

        String raw = aiService.chat(user, AiPrompts.TASK_DETECTION_PROMPT, userContent);

        if (raw == null || !raw.trim().startsWith("{")) {
            log.warn("任务检测跳过，AI 返回非 JSON 内容: {}", raw);
            TaskDetectionResult fallback = new TaskDetectionResult();
            fallback.setTask(false);
            fallback.setMissingFields(List.of());
            return fallback;
        }

        try {
            var node = objectMapper.readTree(AiService.cleanJsonResponse(raw));

            TaskDetectionResult result = new TaskDetectionResult();
            result.setTask(node.path("is_task").asBoolean(false));
            result.setTaskType(node.path("task_type").asText(null));
            result.setRelatedTaskId(node.path("related_task_id").asText(null));

            // extracted
            var extractedNode = node.path("extracted");
            if (!extractedNode.isMissingNode()) {
                TaskDetectionResult.Extracted ex = new TaskDetectionResult.Extracted();
                ex.setName(nullIfEmpty(extractedNode.path("name").asText(null)));
                ex.setCycle(nullIfEmpty(extractedNode.path("cycle").asText(null)));
                ex.setTrigger(nullIfEmpty(extractedNode.path("trigger").asText(null)));
                ex.setCurrentProgress(nullIfEmpty(extractedNode.path("current_progress").asText(null)));
                ex.setEndCondition(nullIfEmpty(extractedNode.path("end_condition").asText(null)));
                result.setExtracted(ex);
            }

            // missing_fields
            var missingNode = node.path("missing_fields");
            if (missingNode.isArray()) {
                List<String> missing = objectMapper.convertValue(missingNode,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
                result.setMissingFields(missing);
            } else {
                result.setMissingFields(List.of());
            }

            return result;
        } catch (Exception e) {
            log.error("任务检测 AI 响应解析失败: raw={}", raw, e);
            // 解析失败时，保守地返回"非任务"
            TaskDetectionResult fallback = new TaskDetectionResult();
            fallback.setTask(false);
            fallback.setMissingFields(List.of());
            return fallback;
        }
    }

    private String nullIfEmpty(String s) {
        if (s == null || s.isBlank() || "null".equalsIgnoreCase(s)) return null;
        return s;
    }
}
