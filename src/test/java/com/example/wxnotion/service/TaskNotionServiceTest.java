package com.example.wxnotion.service;

import com.example.wxnotion.config.NotionProperties;
import com.example.wxnotion.mapper.UserConfigRepository;
import com.example.wxnotion.model.TaskDraft;
import com.example.wxnotion.model.UserConfig;
import com.example.wxnotion.service.facade.NotionApiFacade;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.wxnotion.model.ConversationEntry;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class TaskNotionServiceTest {

    NotionApiFacade facade;
    NotionProperties notionProperties;
    UserConfigRepository userConfigRepository;
    TaskNotionService service;

    @BeforeEach
    void setUp() {
        facade = mock(NotionApiFacade.class);
        notionProperties = new NotionProperties();
        notionProperties.setAdminToken("admin-token");
        notionProperties.setGuestRootPageId("root-page-id");
        userConfigRepository = mock(UserConfigRepository.class);
        service = new TaskNotionService(facade, notionProperties, userConfigRepository, new ObjectMapper());
    }

    @Test
    void ensureTasksDatabase_createsAndSavesId() {
        UserConfig user = makeGuestUser();
        when(facade.createTasksDatabase(anyString(), anyString(), anyString())).thenReturn("new-db-id");

        String dbId = service.ensureTasksDatabase(user);

        assertEquals("new-db-id", dbId);
        assertEquals("new-db-id", user.getTasksDatabaseId());
        verify(userConfigRepository).update(any(), any());
    }

    @Test
    void ensureTasksDatabase_reusesExistingId() {
        UserConfig user = makeGuestUser();
        user.setTasksDatabaseId("existing-db-id");

        String dbId = service.ensureTasksDatabase(user);

        assertEquals("existing-db-id", dbId);
        verify(facade, never()).createTasksDatabase(any(), any(), any());
    }

    @Test
    void appendConversationHistory_skipsEmpty() {
        UserConfig user = makeGuestUser();
        service.appendConversationHistory(user, "page-id", new ArrayList<>());
        verify(facade, never()).appendBlockChildren(any(), any(), any());
    }

    @Test
    void appendConversationHistory_callsAppend() {
        UserConfig user = makeGuestUser();
        List<ConversationEntry> log = List.of(ConversationEntry.of("user", "hello"));
        service.appendConversationHistory(user, "page-id", log);
        verify(facade).appendBlockChildren(eq("admin-token"), eq("page-id"), any());
    }

    private UserConfig makeGuestUser() {
        UserConfig u = new UserConfig();
        u.setOpenId("open123");
        u.setIsGuest(true);
        u.setDatabaseId("notes-db-id");
        return u;
    }
}
