package com.example.wxnotion.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class WxOAuthSessionServiceTest {

    private WxOAuthSessionService sessionService;

    @BeforeEach
    void setUp() {
        sessionService = new WxOAuthSessionService();
    }

    @Test
    void createState_returnsNonNullUniqueStrings() {
        String s1 = sessionService.createState();
        String s2 = sessionService.createState();
        assertNotNull(s1);
        assertNotEquals(s1, s2);
    }

    @Test
    void newState_statusIsPending() {
        String state = sessionService.createState();
        WxOAuthSessionService.SessionStatus status = sessionService.getStatus(state);
        assertEquals("PENDING", status.status);
        assertNull(status.openId);
    }

    @Test
    void unknownState_statusIsExpired() {
        WxOAuthSessionService.SessionStatus status = sessionService.getStatus("not-exist");
        assertEquals("EXPIRED", status.status);
    }

    @Test
    void markAuthed_thenGetStatus_returnsSuccess() {
        String state = sessionService.createState();
        sessionService.markAuthed(state, "wx_open_id_001");

        WxOAuthSessionService.SessionStatus status = sessionService.getStatus(state);
        assertEquals("SUCCESS", status.status);
        assertEquals("wx_open_id_001", status.openId);
    }

    @Test
    void consume_clearsSession() {
        String state = sessionService.createState();
        sessionService.markAuthed(state, "wx_open_id_002");
        sessionService.consume(state);

        WxOAuthSessionService.SessionStatus status = sessionService.getStatus(state);
        assertEquals("EXPIRED", status.status);
    }

    @Test
    void markAuthed_nullArgs_doesNotThrow() {
        assertDoesNotThrow(() -> sessionService.markAuthed(null, "openId"));
        assertDoesNotThrow(() -> sessionService.markAuthed("state", null));
    }
}
