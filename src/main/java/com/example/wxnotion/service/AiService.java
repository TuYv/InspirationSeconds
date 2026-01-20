package com.example.wxnotion.service;

import com.example.wxnotion.http.HttpClient;
import com.example.wxnotion.http.HttpClient.HttpResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 通用 AI 服务 (OpenAI 兼容接口)
 * 支持接入 DeepSeek, SiliconFlow, Moonshot, ChatGPT 等
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${ai.base-url}")
    private String baseUrl;

    @Value("${ai.api-key}")
    private String apiKey;

    @Value("${ai.model}")
    private String model;

    /**
     * 发送聊天请求并获取回复
     * @param systemPrompt 系统提示词 (设定 AI 角色)
     * @param userMessage 用户输入内容
     * @return AI 的回复文本
     */
    public String chat(String systemPrompt, String userMessage) {
        if (apiKey == null || apiKey.equals("sk-0123token") || apiKey.contains("your-key")) {
            log.warn("AI API Key 未配置或为占位符，跳过 AI 调用");
            return """
                ## 🚧 配置未完成
                AI 服务暂未激活。请在后台配置有效的 API Key 以启用每日总结功能。
                
                (当前 Key 为占位符: `sk-0123token`)
                """;
        }

        try {
            // 构造 OpenAI 格式的请求体
            ObjectNode requestBody = mapper.createObjectNode();
            requestBody.put("model", model);
            requestBody.put("temperature", 0.7); // 增加一点创造性

            ArrayNode messages = requestBody.putArray("messages");
            
            // System Message
            ObjectNode sysMsg = messages.addObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", systemPrompt);

            // User Message
            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);

            String json = mapper.writeValueAsString(requestBody);
            
            // 构造 URL (处理结尾可能多余的 /)
            String endpoint = baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";

            HttpResponse resp = httpClient.execute(new HttpClient.HttpRequest(endpoint, "POST", json, buildHeaders()));

            if (!resp.isSuccessful) {
                log.error("AI 请求失败: Code={}, Body={}", resp.code, resp.body);
                return "AI 服务暂时不可用 (Code " + resp.code + ")";
            }

            // 解析响应
            JsonNode root = mapper.readTree(resp.body);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                return choices.get(0).path("message").path("content").asText();
            }
            
            return "AI 未返回有效内容";

        } catch (IOException e) {
            log.error("AI 调用异常: {}", e.getMessage(), e);
            return "AI 服务连接错误";
        }
    }

    private Map<String, String> buildHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + apiKey);
        headers.put("Content-Type", "application/json");
        return headers;
    }
}
