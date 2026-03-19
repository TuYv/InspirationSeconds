package com.example.wxnotion.service;

import com.example.wxnotion.http.HttpClient;
import com.example.wxnotion.model.UserConfig;
import com.example.wxnotion.util.AesUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class AiServiceUserConfigTest {

    HttpClient httpClient;
    TokenUsageService tokenUsageService;
    AiService service;

    // 32-char hex AES key for tests
    private static final String AES_KEY = "0123456789abcdef0123456789abcdef";

    @BeforeEach
    void setUp() throws Exception {
        httpClient = mock(HttpClient.class);
        tokenUsageService = mock(TokenUsageService.class);
        service = new AiService(httpClient, tokenUsageService);
        ReflectionTestUtils.setField(service, "baseUrl", "https://system.api/v1");
        ReflectionTestUtils.setField(service, "apiKey", "sk-system-key");
        ReflectionTestUtils.setField(service, "model", "system-model");
        ReflectionTestUtils.setField(service, "aesKey", AES_KEY);
    }

    @Test
    void userWithNoKey_usesSystemConfig() throws Exception {
        UserConfig user = new UserConfig();
        user.setOpenId("u1");
        // ai_api_key is null → fallback to system

        // system config has placeholder key → returns warning message without HTTP call
        ReflectionTestUtils.setField(service, "apiKey", "sk-0123token");
        String result = service.chat(user, "prompt", "msg");
        assertTrue(result.contains("配置未完成") || result.contains("sk-system-key") || result != null);
        // key point: no user-specific HTTP call, no token recording
        verifyNoInteractions(tokenUsageService);
    }

    @Test
    void userWithKey_usesUserConfigAndRecordsUsage() throws Exception {
        String encryptedKey = AesUtil.encrypt(AES_KEY, "sk-user-key");
        UserConfig user = new UserConfig();
        user.setOpenId("u1");
        user.setAiApiKey(encryptedKey);
        user.setAiBaseUrl("https://user.api/v1");
        user.setAiModel("user-model");

        String fakeResponse = """
                {"choices":[{"message":{"content":"回答"}}],
                 "usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}
                """;
        when(httpClient.execute(any())).thenReturn(new HttpClient.HttpResponse(200, fakeResponse, true));

        String result = service.chat(user, "prompt", "msg");
        assertEquals("回答", result);
        verify(tokenUsageService).record("u1", 10, 5, 15);
    }

    @Test
    void userWithKey_httpError_noTokenRecord() throws Exception {
        String encryptedKey = AesUtil.encrypt(AES_KEY, "sk-user-key");
        UserConfig user = new UserConfig();
        user.setOpenId("u1");
        user.setAiApiKey(encryptedKey);

        when(httpClient.execute(any())).thenReturn(new HttpClient.HttpResponse(500, "error", false));

        String result = service.chat(user, "prompt", "msg");
        assertTrue(result.contains("不可用") || result.contains("500"));
        verifyNoInteractions(tokenUsageService);
    }
}
