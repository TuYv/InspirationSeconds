package com.example.wxnotion.service;

import com.example.wxnotion.AbstractSpringTest;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class AiServiceTest extends AbstractSpringTest {

    static MockWebServer mockWebServer;

    @BeforeAll
    static void startServer() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterAll
    static void stopServer() throws IOException {
        mockWebServer.shutdown();
    }

    @DynamicPropertySource
    static void overrideAiBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("ai.base-url", () -> mockWebServer.url("/v1").toString());
    }

    @Autowired
    private AiService aiService;

    // ── 路径 1：API Key 为占位符，直接短路返回 ─────────────────────────────

    @Test
    void chat_placeholderApiKey_returnsConfigPrompt() {
        String original = (String) ReflectionTestUtils.getField(aiService, "apiKey");
        try {
            ReflectionTestUtils.setField(aiService, "apiKey", "sk-0123token");
            String result = aiService.chat("system", "user");
            assertTrue(result.contains("配置未完成"), "占位 Key 应返回配置提示，实际：" + result);
        } finally {
            ReflectionTestUtils.setField(aiService, "apiKey", original);
        }
    }

    // ── 路径 2：HTTP 成功，解析 choices[0].message.content ────────────────

    @Test
    void chat_successResponse_returnsContent() {
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""
                {
                  "choices": [
                    { "message": { "content": "这是 AI 的回复" } }
                  ]
                }
                """));

        String result = aiService.chat("你是助手", "今天发生了什么");
        assertEquals("这是 AI 的回复", result);
    }

    // ── 路径 3：HTTP 返回 5xx，返回不可用提示 ────────────────────────────

    @Test
    void chat_httpError_returnsUnavailableMessage() {
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(500)
            .setBody("Internal Server Error"));

        String result = aiService.chat("system", "user");
        assertTrue(result.contains("不可用") || result.contains("Code 500"),
            "HTTP 错误应返回不可用提示，实际：" + result);
    }
}
