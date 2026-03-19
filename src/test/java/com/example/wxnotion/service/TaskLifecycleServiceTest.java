package com.example.wxnotion.service;

import com.example.wxnotion.model.TaskTerminationResult;
import com.example.wxnotion.model.UserConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class TaskLifecycleServiceTest {

    AiService aiService;
    TaskNotionService taskNotionService;
    TaskReminderService taskReminderService;
    TaskLifecycleService service;
    UserConfig user;

    @BeforeEach
    void setUp() {
        aiService = mock(AiService.class);
        taskNotionService = mock(TaskNotionService.class);
        taskReminderService = mock(TaskReminderService.class);
        service = new TaskLifecycleService(taskNotionService, taskReminderService, aiService);
        user = new UserConfig();
        user.setOpenId("u1");
    }

    @Test
    void updateProgress_appendsHistoryAndUpdatesProperty() {
        String reply = service.updateProgress(user, "page1", "读书计划", "今天读了5页");
        verify(taskNotionService).appendConversationHistory(eq(user), eq("page1"), anyList());
        verify(taskNotionService).updateTaskProgress(eq(user), eq("page1"), eq("今天读了5页"));
        assertTrue(reply.contains("读书计划"));
    }

    @Test
    void detectTerminationIntent_completed() throws Exception {
        TaskTerminationResult aiResult = new TaskTerminationResult();
        aiResult.isTermination = true;
        aiResult.status = "completed";
        aiResult.taskPageId = "p1";
        aiResult.taskName = "读书计划";
        when(aiService.chatForObject(any(UserConfig.class), any(), any(), eq(TaskTerminationResult.class)))
                .thenReturn(aiResult);
        var intent = service.detectTerminationIntent(user, "读完了", "[{\"id\":\"p1\",\"name\":\"读书计划\"}]");
        assertTrue(intent.isTermination);
        assertEquals("completed", intent.status);
        assertEquals("p1", intent.taskPageId);
        assertEquals("读书计划", intent.taskName);
    }

    @Test
    void detectTerminationIntent_notTermination() throws Exception {
        TaskTerminationResult aiResult = new TaskTerminationResult();
        when(aiService.chatForObject(any(UserConfig.class), any(), any(), eq(TaskTerminationResult.class)))
                .thenReturn(aiResult);
        var intent = service.detectTerminationIntent(user, "今天跑了5公里", "[]");
        assertFalse(intent.isTermination);
        assertNull(intent.taskPageId);
    }

    @Test
    void executeTermination_completedReply() {
        String reply = service.executeTermination(user, "p1", "读书计划", "completed");
        verify(taskNotionService).updateTaskStatus(eq(user), eq("p1"), eq("completed"));
        verify(taskReminderService).cancelReminder("p1");
        assertTrue(reply.contains("已完成"));
        assertTrue(reply.contains("读书计划"));
    }

    @Test
    void executeTermination_abandonedReply() {
        String reply = service.executeTermination(user, "p2", "健身", "abandoned");
        assertTrue(reply.contains("放弃"));
        assertTrue(reply.contains("健身"));
    }

    @Test
    void executeTermination_deletedReply() {
        String reply = service.executeTermination(user, "p3", "旧任务", "deleted");
        assertTrue(reply.contains("删除") || reply.contains("旧任务"));
    }
}
