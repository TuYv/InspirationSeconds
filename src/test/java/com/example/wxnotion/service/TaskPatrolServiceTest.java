package com.example.wxnotion.service;

import com.example.wxnotion.mapper.UserConfigRepository;
import com.example.wxnotion.model.TaskPatrolItem;
import com.example.wxnotion.model.UserConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class TaskPatrolServiceTest {

    UserConfigRepository userConfigRepository;
    TaskNotionService taskNotionService;
    WechatService wechatService;
    AiService aiService;
    TaskPatrolService service;

    @BeforeEach
    void setUp() {
        userConfigRepository = mock(UserConfigRepository.class);
        taskNotionService = mock(TaskNotionService.class);
        wechatService = mock(WechatService.class);
        aiService = mock(AiService.class);
        service = new TaskPatrolService(userConfigRepository, taskNotionService, wechatService, aiService);
    }

    @Test
    void patrol_noActiveUsers_doesNothing() {
        when(userConfigRepository.selectActiveUsers()).thenReturn(List.of());
        service.patrol();
        verifyNoInteractions(taskNotionService);
        verifyNoInteractions(wechatService);
    }

    @Test
    void patrol_aiReturnsReminders_pushesMessages() {
        UserConfig u = new UserConfig();
        u.setOpenId("u1");
        when(userConfigRepository.selectActiveUsers()).thenReturn(List.of(u));
        when(taskNotionService.getActiveTaskSummary(u)).thenReturn(
                "[{\"id\":\"p1\",\"name\":\"读书计划\"}]");
        TaskPatrolItem item = new TaskPatrolItem();
        item.taskId = "p1";
        item.taskName = "读书计划";
        item.remindMessage = "记得更新进度哦";
        item.progress = "";
        try {
            when(aiService.chatForList(any(UserConfig.class), any(), any(), any())).thenReturn(List.of(item));
        } catch (Exception e) { throw new RuntimeException(e); }

        service.patrol();

        verify(wechatService).pushTemplateMessage(eq("u1"), eq("读书计划"), eq("记得更新进度哦"), eq(""));
    }

    @Test
    void patrol_aiReturnsEmpty_noMessages() {
        UserConfig u = new UserConfig();
        u.setOpenId("u1");
        when(userConfigRepository.selectActiveUsers()).thenReturn(List.of(u));
        when(taskNotionService.getActiveTaskSummary(u)).thenReturn(
                "[{\"id\":\"p1\",\"name\":\"读书计划\"}]");
        try {
            when(aiService.chatForList(any(UserConfig.class), any(), any(), any())).thenReturn(List.of());
        } catch (Exception e) { throw new RuntimeException(e); }

        service.patrol();

        verifyNoInteractions(wechatService);
    }

    @Test
    void patrol_oneUserFails_continuesForOthers() {
        UserConfig u1 = new UserConfig(); u1.setOpenId("u1");
        UserConfig u2 = new UserConfig(); u2.setOpenId("u2");
        when(userConfigRepository.selectActiveUsers()).thenReturn(List.of(u1, u2));
        when(taskNotionService.getActiveTaskSummary(u1)).thenThrow(new RuntimeException("Notion error"));
        when(taskNotionService.getActiveTaskSummary(u2)).thenReturn("[]");

        // Should not throw
        service.patrol();
        // u2 processed (no messages since empty)
        verifyNoInteractions(wechatService);
    }
}
