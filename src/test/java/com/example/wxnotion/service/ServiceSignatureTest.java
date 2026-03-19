package com.example.wxnotion.service;

import com.example.wxnotion.mapper.TaskDraftMapper;
import com.example.wxnotion.model.TaskDraft;
import com.example.wxnotion.model.TaskDraftData;
import com.example.wxnotion.model.TaskDetectionResult;
import com.example.wxnotion.model.TaskTerminationResult;
import com.example.wxnotion.model.UserConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 验证各服务签名变更后能正确调用 AiService.chat(UserConfig, ...)。
 */
public class ServiceSignatureTest {

    AiService aiService;
    ObjectMapper objectMapper = new ObjectMapper();
    UserConfig user;

    @BeforeEach
    void setUp() {
        aiService = mock(AiService.class);
        user = new UserConfig();
        user.setOpenId("u1");
        // Default AI response fallback
        when(aiService.chat(any(UserConfig.class), any(), any())).thenReturn(
                "{\"is_task\":false,\"task_type\":null,\"extracted\":{}," +
                "\"missing_fields\":[],\"related_task_id\":null}");
    }

    @Test
    void taskDetectionService_passesUserConfig() {
        TaskDetectionService svc = new TaskDetectionService(aiService, objectMapper);
        svc.detectTask(user, "今天吃了拉面", "[]");
        verify(aiService).chat(eq(user), anyString(), anyString());
    }

    @Test
    void taskDraftService_generateClarification_passesUserConfig() {
        when(aiService.chat(any(UserConfig.class), any(), any())).thenReturn("你的任务叫什么？");
        TaskDraftMapper draftMapper = mock(TaskDraftMapper.class);
        TaskDraftService svc = new TaskDraftService(draftMapper, aiService);

        TaskDraft draft = new TaskDraft();
        TaskDraftData draftJson = new TaskDraftData();
        draftJson.setTaskType("recurring");
        draftJson.setMissingFields(new ArrayList<>(List.of("name")));
        draft.setDraftJson(draftJson);
        draft.setConversationLog(new ArrayList<>());

        svc.generateClarificationQuestion(user, draft);
        verify(aiService).chat(eq(user), anyString(), anyString());
    }

    @Test
    void taskLifecycleService_detectTermination_passesUserConfig() throws Exception {
        when(aiService.chatForObject(any(UserConfig.class), any(), any(), eq(TaskTerminationResult.class)))
                .thenReturn(new TaskTerminationResult());
        TaskNotionService notionService = mock(TaskNotionService.class);
        TaskReminderService reminderService = mock(TaskReminderService.class);
        TaskLifecycleService svc = new TaskLifecycleService(notionService, reminderService, aiService);

        svc.detectTerminationIntent(user, "普通消息", "[]");
        verify(aiService).chatForObject(eq(user), anyString(), anyString(), eq(TaskTerminationResult.class));
    }
}
