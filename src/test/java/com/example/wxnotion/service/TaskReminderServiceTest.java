package com.example.wxnotion.service;

import com.example.wxnotion.model.UserConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class TaskReminderServiceTest {

    AiService aiService;
    Scheduler scheduler;
    TaskReminderService service;
    UserConfig user;

    @BeforeEach
    void setUp() throws Exception {
        aiService = mock(AiService.class);
        scheduler = mock(Scheduler.class);
        service = new TaskReminderService(scheduler, aiService, new ObjectMapper());
        user = new UserConfig();
        user.setOpenId("u1");
    }

    @Test
    void generateCron_validAiResponse() {
        when(aiService.chat(any(UserConfig.class), any(), any())).thenReturn("{\"cron\":\"0 0 9 * * ?\"}");
        String cron = service.generateCronForTask(user, "晨跑", "recurring", "每天");
        assertEquals("0 0 9 * * ?", cron);
    }

    @Test
    void generateCron_invalidAiResponseFallsBack() {
        when(aiService.chat(any(UserConfig.class), any(), any())).thenReturn("{\"cron\":\"not-a-cron\"}");
        String cron = service.generateCronForTask(user, "任务", "once", null);
        assertEquals("0 0 20 * * ?", cron);
    }

    @Test
    void generateCron_malformedJsonFallsBack() {
        when(aiService.chat(any(UserConfig.class), any(), any())).thenReturn("oops, not json");
        String cron = service.generateCronForTask(user, "任务", "once", null);
        assertEquals("0 0 20 * * ?", cron);
    }

    @Test
    void scheduleReminder_registersJob() throws Exception {
        service.scheduleReminder(user, "page1", "读书计划", "0 0 20 * * ?");
        verify(scheduler).deleteJob(any(JobKey.class));
        verify(scheduler).scheduleJob(any(JobDetail.class), any(Trigger.class));
    }

    @Test
    void cancelReminder_deletesJob() throws Exception {
        service.cancelReminder("page1");
        verify(scheduler).deleteJob(any(JobKey.class));
    }
}
