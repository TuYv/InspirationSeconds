package com.example.wxnotion.service;

import com.example.wxnotion.mapper.TokenUsageMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class TokenUsageServiceTest {

    TokenUsageMapper mapper;
    TokenUsageService service;

    @BeforeEach
    void setUp() {
        mapper = mock(TokenUsageMapper.class);
        service = new TokenUsageService(mapper);
    }

    @Test
    void record_callsUpsert() {
        service.record("u1", 100, 50, 150);
        verify(mapper).upsertUsage(eq("u1"), any(LocalDate.class), eq(100), eq(50), eq(150));
    }

    @Test
    void record_mapperThrows_doesNotPropagate() {
        doThrow(new RuntimeException("DB error")).when(mapper).upsertUsage(any(), any(), anyInt(), anyInt(), anyInt());
        // should not throw
        service.record("u1", 10, 5, 15);
    }
}
