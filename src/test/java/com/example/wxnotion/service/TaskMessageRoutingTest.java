package com.example.wxnotion.service;

import com.example.wxnotion.mapper.UserConfigRepository;
import com.example.wxnotion.model.TaskDetectionResult;
import com.example.wxnotion.model.TaskDraft;
import com.example.wxnotion.model.TaskDraftData;
import com.example.wxnotion.model.TaskTerminationResult;
import com.example.wxnotion.model.UserConfig;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.bean.message.WxMpXmlMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class TaskMessageRoutingTest {

    SyncService syncService;
    WxMpService wxMpService;
    WechatService wechatService;
    UserConfigRepository userConfigRepository;
    TaskDetectionService taskDetectionService;
    TaskDraftService taskDraftService;
    TaskNotionService taskNotionService;
    TaskLifecycleService taskLifecycleService;
    TaskReminderService taskReminderService;
    HandlerWxPortalService handler;

    @BeforeEach
    void setUp() {
        syncService = mock(SyncService.class);
        wxMpService = mock(WxMpService.class);
        wechatService = mock(WechatService.class);
        userConfigRepository = mock(UserConfigRepository.class);
        taskDetectionService = mock(TaskDetectionService.class);
        taskDraftService = mock(TaskDraftService.class);
        taskNotionService = mock(TaskNotionService.class);
        taskLifecycleService = mock(TaskLifecycleService.class);
        taskReminderService = mock(TaskReminderService.class);

        handler = new HandlerWxPortalService(
                syncService, wxMpService, wechatService,
                userConfigRepository, taskDetectionService, taskDraftService,
                taskNotionService, taskLifecycleService, taskReminderService);

        ApplicationContext ctx = mock(ApplicationContext.class);
        when(ctx.getBean(HandlerWxPortalService.class)).thenReturn(handler);
        handler.setApplicationContext(ctx);
    }

    @Test
    void ordinaryNote_callsSyncService() {
        UserConfig user = makeUser();
        when(userConfigRepository.selectByOpenId(anyString())).thenReturn(user);
        when(taskDraftService.findPendingDrafts(anyString())).thenReturn(List.of());
        when(taskDetectionService.detectTask(any(), anyString(), anyString())).thenReturn(notTaskResult());
        when(taskNotionService.getActiveTaskSummary(any())).thenReturn("[]");
        when(syncService.sync(anyString(), anyString())).thenReturn("已同步");

        // termination intent not detected
        TaskTerminationResult noIntent = new TaskTerminationResult();
        when(taskLifecycleService.detectTerminationIntent(any(), anyString(), anyString())).thenReturn(noIntent);

        WxMpXmlMessage msg = makeMessage("today ate ramen");
        handler.handle(msg);

        try { Thread.sleep(500); } catch (InterruptedException ignored) {}
        verify(syncService).sync(eq("openid123"), eq("today ate ramen"));
    }

    @Test
    void newTask_doesNotCallSyncService() {
        UserConfig user = makeUser();
        when(userConfigRepository.selectByOpenId(anyString())).thenReturn(user);
        when(taskDraftService.findPendingDrafts(anyString())).thenReturn(List.of());
        when(taskDetectionService.detectTask(any(), anyString(), anyString())).thenReturn(taskResult());
        when(taskNotionService.getActiveTaskSummary(any())).thenReturn("[]");
        TaskDraft draft = makeDraft(List.of("end_condition"));
        when(taskDraftService.createDraft(anyString(), anyString(), any())).thenReturn(draft);
        when(taskDraftService.isDraftComplete(any())).thenReturn(false);
        when(taskDraftService.generateClarificationQuestion(any(), any())).thenReturn("什么时候算完成？");

        WxMpXmlMessage msg = makeMessage("我要每天跑步");
        handler.handle(msg);

        try { Thread.sleep(500); } catch (InterruptedException ignored) {}
        verify(syncService, never()).sync(anyString(), anyString());
        verify(wechatService).pushMessageToUser(eq("openid123"), eq("什么时候算完成？"));
    }

    @Test
    void pendingDraftMatched_doesNotCallSyncService() {
        UserConfig user = makeUser();
        when(userConfigRepository.selectByOpenId(anyString())).thenReturn(user);
        TaskDraft draft = makeDraft(List.of("end_condition"));
        when(taskDraftService.findPendingDrafts(anyString())).thenReturn(List.of(draft));
        when(taskDraftService.matchReplyToDraft(any(), anyString())).thenReturn(draft);
        when(taskDraftService.isDraftComplete(any())).thenReturn(false);
        when(taskDraftService.generateClarificationQuestion(any(), any())).thenReturn("完成标准？");

        WxMpXmlMessage msg = makeMessage("坚持30天");
        handler.handle(msg);

        try { Thread.sleep(500); } catch (InterruptedException ignored) {}
        verify(syncService, never()).sync(anyString(), anyString());
    }

    @Test
    void relatedTask_routesToProgressUpdate() {
        UserConfig user = makeUser();
        when(userConfigRepository.selectByOpenId(anyString())).thenReturn(user);
        when(taskDraftService.findPendingDrafts(anyString())).thenReturn(List.of());
        when(taskNotionService.getActiveTaskSummary(any())).thenReturn("[]");

        TaskDetectionResult r = notTaskResult();
        r.setRelatedTaskId("page1");
        TaskDetectionResult.Extracted ex = new TaskDetectionResult.Extracted();
        ex.setName("读书计划");
        r.setExtracted(ex);
        when(taskDetectionService.detectTask(any(), anyString(), anyString())).thenReturn(r);
        when(taskLifecycleService.updateProgress(any(), eq("page1"), eq("读书计划"), anyString()))
                .thenReturn("✅ 进度已记录：读书计划");

        WxMpXmlMessage msg = makeMessage("今天读了10页");
        handler.handle(msg);

        try { Thread.sleep(500); } catch (InterruptedException ignored) {}
        verify(taskLifecycleService).updateProgress(eq(user), eq("page1"), eq("读书计划"), eq("今天读了10页"));
        verify(syncService, never()).sync(anyString(), anyString());
    }

    @Test
    void terminationIntent_executesTermination() {
        UserConfig user = makeUser();
        when(userConfigRepository.selectByOpenId(anyString())).thenReturn(user);
        when(taskDraftService.findPendingDrafts(anyString())).thenReturn(List.of());
        when(taskDetectionService.detectTask(any(), anyString(), anyString())).thenReturn(notTaskResult());
        when(taskNotionService.getActiveTaskSummary(any())).thenReturn("[]");

        TaskTerminationResult intent = new TaskTerminationResult();
        intent.isTermination = true;
        intent.taskPageId = "p1";
        intent.taskName = "健身";
        intent.status = "completed";
        when(taskLifecycleService.detectTerminationIntent(any(), anyString(), anyString())).thenReturn(intent);
        when(taskLifecycleService.executeTermination(any(), eq("p1"), eq("健身"), eq("completed")))
                .thenReturn("✅ 任务「健身」已完成，恭喜！");

        WxMpXmlMessage msg = makeMessage("锻炼完成了");
        handler.handle(msg);

        try { Thread.sleep(500); } catch (InterruptedException ignored) {}
        verify(taskLifecycleService).executeTermination(eq(user), eq("p1"), eq("健身"), eq("completed"));
        verify(syncService, never()).sync(anyString(), anyString());
    }

    // ── helpers ──

    private UserConfig makeUser() {
        UserConfig u = new UserConfig();
        u.setOpenId("openid123");
        u.setIsGuest(true);
        return u;
    }

    private WxMpXmlMessage makeMessage(String content) {
        WxMpXmlMessage msg = new WxMpXmlMessage();
        msg.setFromUser("openid123");
        msg.setMsgType("text");
        msg.setContent(content);
        return msg;
    }

    private TaskDetectionResult notTaskResult() {
        TaskDetectionResult r = new TaskDetectionResult();
        r.setTask(false);
        r.setMissingFields(List.of());
        return r;
    }

    private TaskDetectionResult taskResult() {
        TaskDetectionResult r = new TaskDetectionResult();
        r.setTask(true);
        r.setTaskType("recurring");
        r.setMissingFields(List.of("end_condition"));
        TaskDetectionResult.Extracted ex = new TaskDetectionResult.Extracted();
        ex.setName("每天跑步");
        r.setExtracted(ex);
        return r;
    }

    private TaskDraft makeDraft(List<String> missingFields) {
        TaskDraft d = new TaskDraft();
        d.setId(1L);
        d.setOpenId("openid123");
        TaskDraftData draftJson = new TaskDraftData();
        draftJson.setName("每天跑步");
        draftJson.setMissingFields(new ArrayList<>(missingFields));
        d.setDraftJson(draftJson);
        d.setConversationLog(new ArrayList<>());
        d.setStatus("PENDING");
        return d;
    }
}
