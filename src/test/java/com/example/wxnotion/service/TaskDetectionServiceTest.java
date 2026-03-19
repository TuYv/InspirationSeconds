package com.example.wxnotion.service;

import com.example.wxnotion.model.TaskDetectionResult;
import com.example.wxnotion.model.UserConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class TaskDetectionServiceTest {

    AiService aiService;
    TaskDetectionService service;
    UserConfig user;

    @BeforeEach
    void setUp() {
        aiService = mock(AiService.class);
        service = new TaskDetectionService(aiService, new ObjectMapper());
        user = new UserConfig();
        user.setOpenId("u1");
    }

    @Test
    void ordinaryNote_notTask() {
        when(aiService.chat(any(UserConfig.class), any(), any())).thenReturn(
                "{\"is_task\":false,\"task_type\":null,\"extracted\":{\"name\":null,\"cycle\":null,\"trigger\":null,\"current_progress\":null,\"end_condition\":null},\"missing_fields\":[],\"related_task_id\":null}");

        TaskDetectionResult result = service.detectTask(user, "今天吃了好吃的拉面", "[]");

        assertFalse(result.isTask());
        assertTrue(result.getMissingFields().isEmpty());
    }

    @Test
    void recurringTask_detected() {
        when(aiService.chat(any(UserConfig.class), any(), any())).thenReturn(
                "{\"is_task\":true,\"task_type\":\"recurring\",\"extracted\":{\"name\":\"每天背50个单词\",\"cycle\":\"每天\",\"trigger\":null,\"current_progress\":null,\"end_condition\":null},\"missing_fields\":[\"end_condition\"],\"related_task_id\":null}");

        TaskDetectionResult result = service.detectTask(user, "我要每天背50个单词", "[]");

        assertTrue(result.isTask());
        assertEquals("recurring", result.getTaskType());
        assertEquals("每天背50个单词", result.getExtracted().getName());
        assertEquals("每天", result.getExtracted().getCycle());
        assertTrue(result.getMissingFields().contains("end_condition"));
    }

    @Test
    void oneTimeTask_detected() {
        when(aiService.chat(any(UserConfig.class), any(), any())).thenReturn(
                "{\"is_task\":true,\"task_type\":\"one_time\",\"extracted\":{\"name\":\"读完《原则》\",\"cycle\":null,\"trigger\":null,\"current_progress\":\"第58页\",\"end_condition\":\"读完全书\"},\"missing_fields\":[],\"related_task_id\":null}");

        TaskDetectionResult result = service.detectTask(user, "我要三月底读完《原则》，现在在第58页", "[]");

        assertTrue(result.isTask());
        assertEquals("one_time", result.getTaskType());
        assertEquals("第58页", result.getExtracted().getCurrentProgress());
        assertEquals("读完全书", result.getExtracted().getEndCondition());
        assertTrue(result.getMissingFields().isEmpty());
    }

    @Test
    void malformedAiResponse_fallsBackToNonTask() {
        when(aiService.chat(any(UserConfig.class), any(), any())).thenReturn("这不是JSON");

        TaskDetectionResult result = service.detectTask(user, "some message", "[]");

        assertFalse(result.isTask());
        assertNotNull(result.getMissingFields());
    }
}
