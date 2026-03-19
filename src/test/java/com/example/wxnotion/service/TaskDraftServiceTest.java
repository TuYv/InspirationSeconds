package com.example.wxnotion.service;

import com.example.wxnotion.mapper.TaskDraftMapper;
import com.example.wxnotion.model.TaskDetectionResult;
import com.example.wxnotion.model.TaskDraft;
import com.example.wxnotion.model.TaskDraftData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class TaskDraftServiceTest {

    TaskDraftMapper taskDraftMapper;
    AiService aiService;
    TaskDraftService service;

    @BeforeEach
    void setUp() {
        taskDraftMapper = mock(TaskDraftMapper.class);
        aiService = mock(AiService.class);
        service = new TaskDraftService(taskDraftMapper, aiService);
    }

    @Test
    void createDraft_insertsCalled() {
        TaskDetectionResult result = makeResult("每天跑步", "recurring", List.of("end_condition"));

        service.createDraft("user123", "我要每天跑步", result);

        verify(taskDraftMapper).insert(any(TaskDraft.class));
    }

    @Test
    void applyReply_removesMissingField() {
        TaskDraft draft = makeDraft(new ArrayList<>(List.of("end_condition", "cycle")));

        service.applyReplyToDraft(draft, "end_condition", "坚持30天", "坚持30天", null);

        List<String> missing = draft.getDraftJson().getMissingFields();
        assertFalse(missing.contains("end_condition"));
        assertTrue(missing.contains("cycle"));
    }

    @Test
    void isDraftComplete_trueWhenNoMissing() {
        TaskDraft draft = makeDraft(new ArrayList<>());

        assertTrue(service.isDraftComplete(draft));
    }

    @Test
    void isDraftComplete_falseWhenMissingFieldsExist() {
        TaskDraft draft = makeDraft(new ArrayList<>(List.of("end_condition")));

        assertFalse(service.isDraftComplete(draft));
    }

    @Test
    void expireOldDrafts_callsUpdate() {
        service.expireOldDrafts();
        verify(taskDraftMapper).update(any(), any());
    }

    // ── helpers ──

    private TaskDetectionResult makeResult(String name, String type, List<String> missing) {
        TaskDetectionResult r = new TaskDetectionResult();
        r.setTask(true);
        r.setTaskType(type);
        r.setMissingFields(missing);
        TaskDetectionResult.Extracted ex = new TaskDetectionResult.Extracted();
        ex.setName(name);
        r.setExtracted(ex);
        return r;
    }

    private TaskDraft makeDraft(List<String> missingFields) {
        TaskDraft draft = new TaskDraft();
        draft.setId(1L);
        draft.setOpenId("user123");
        TaskDraftData data = new TaskDraftData();
        data.setName("每天跑步");
        data.setTaskType("recurring");
        data.setMissingFields(missingFields);
        draft.setDraftJson(data);
        draft.setConversationLog(new ArrayList<>());
        draft.setStatus("PENDING");
        draft.setCreatedAt(LocalDateTime.now());
        draft.setUpdatedAt(LocalDateTime.now());
        return draft;
    }
}
