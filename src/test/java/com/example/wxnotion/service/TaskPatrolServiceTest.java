package com.example.wxnotion.service;

import com.example.wxnotion.mapper.UserConfigRepository;
import com.example.wxnotion.model.ConfigStatus;
import com.example.wxnotion.model.UserConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        service = new TaskPatrolService(userConfigRepository, taskNotionService, wechatService, aiService, new ObjectMapper());
    }

    @Test
    void patrol_noActiveUsers_doesNothing() {
        when(userConfigRepository.selectList(any())).thenReturn(List.of());
        service.patrol();
        verifyNoInteractions(taskNotionService);
        verifyNoInteractions(wechatService);
    }

    @Test
    void patrol_aiReturnsReminders_pushesMessages() {
        UserConfig u = new UserConfig();
        u.setOpenId("u1");
        when(userConfigRepository.selectList(any())).thenReturn(List.of(u));
        when(taskNotionService.getActiveTaskSummary(u)).thenReturn(
                "[{\"id\":\"p1\",\"name\":\"读书计划\"}]");
        when(aiService.chat(any(com.example.wxnotion.model.UserConfig.class), any(), any())).thenReturn(
                "[{\"task_id\":\"p1\",\"task_name\":\"读书计划\",\"remind_message\":\"记得更新进度哦\",\"progress\":\"\"}]");

        service.patrol();

        verify(wechatService).pushTemplateMessage(eq("u1"), eq("读书计划"), eq("记得更新进度哦"), eq(""));
    }

    @Test
    void patrol_aiReturnsEmpty_noMessages() {
        UserConfig u = new UserConfig();
        u.setOpenId("u1");
        when(userConfigRepository.selectList(any())).thenReturn(List.of(u));
        when(taskNotionService.getActiveTaskSummary(u)).thenReturn(
                "[{\"id\":\"p1\",\"name\":\"读书计划\"}]");
        when(aiService.chat(any(com.example.wxnotion.model.UserConfig.class), any(), any())).thenReturn("[]");

        service.patrol();

        verifyNoInteractions(wechatService);
    }

    @Test
    void patrol_oneUserFails_continuesForOthers() {
        UserConfig u1 = new UserConfig(); u1.setOpenId("u1");
        UserConfig u2 = new UserConfig(); u2.setOpenId("u2");
        when(userConfigRepository.selectList(any())).thenReturn(List.of(u1, u2));
        when(taskNotionService.getActiveTaskSummary(u1)).thenThrow(new RuntimeException("Notion error"));
        when(taskNotionService.getActiveTaskSummary(u2)).thenReturn("[]");

        // Should not throw
        service.patrol();
        // u2 processed (no messages since empty)
        verifyNoInteractions(wechatService);
    }
}
