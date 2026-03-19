package com.example.wxnotion.service;

import com.example.wxnotion.http.HttpClient;
import com.example.wxnotion.http.HttpClient.HttpResponse;
import com.example.wxnotion.model.UserConfig;
import com.example.wxnotion.util.AesUtil;
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
import java.util.List;
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
    private final TokenUsageService tokenUsageService;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${ai.base-url}")
    private String baseUrl;

    @Value("${ai.api-key}")
    private String apiKey;

    @Value("${ai.model}")
    private String model;

    @Value("${security.aesKey}")
    private String aesKey;

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

    /**
     * 用户感知版：优先使用用户自定义配置，未配置则委托系统配置。
     * 有自有 Key 时解析 usage 并写入 token_usage 表。
     */
    public String chat(UserConfig user, String systemPrompt, String userMessage) {
        if (user == null || user.getAiApiKey() == null || user.getAiApiKey().isBlank()) {
            return chat(systemPrompt, userMessage);
        }

        String userApiKey;
        try {
            userApiKey = AesUtil.decrypt(aesKey, user.getAiApiKey());
        } catch (Exception e) {
            log.warn("解密用户 AI Key 失败，用户: {}, fallback 系统配置", user.getOpenId());
            return chat(systemPrompt, userMessage);
        }

        String effectiveBaseUrl = (user.getAiBaseUrl() != null && !user.getAiBaseUrl().isBlank())
                ? user.getAiBaseUrl() : baseUrl;
        String effectiveModel = (user.getAiModel() != null && !user.getAiModel().isBlank())
                ? user.getAiModel() : model;

        try {
            ObjectNode requestBody = mapper.createObjectNode();
            requestBody.put("model", effectiveModel);
            requestBody.put("temperature", 0.7);

            ArrayNode messages = requestBody.putArray("messages");
            messages.addObject().put("role", "system").put("content", systemPrompt);
            messages.addObject().put("role", "user").put("content", userMessage);

            String json = mapper.writeValueAsString(requestBody);
            String endpoint = effectiveBaseUrl.endsWith("/")
                    ? effectiveBaseUrl + "chat/completions"
                    : effectiveBaseUrl + "/chat/completions";

            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + userApiKey);
            headers.put("Content-Type", "application/json");

            HttpResponse resp = httpClient.execute(new HttpClient.HttpRequest(endpoint, "POST", json, headers));

            if (!resp.isSuccessful) {
                log.error("用户 AI 请求失败: Code={}, Body={}, 用户: {}", resp.code, resp.body, user.getOpenId());
                return "AI 服务暂时不可用 (Code " + resp.code + ")";
            }

            JsonNode root = mapper.readTree(resp.body);

            // 记录 token 用量
            JsonNode usage = root.path("usage");
            if (!usage.isMissingNode()) {
                tokenUsageService.record(
                        user.getOpenId(),
                        usage.path("prompt_tokens").asInt(0),
                        usage.path("completion_tokens").asInt(0),
                        usage.path("total_tokens").asInt(0));
            }

            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                return choices.get(0).path("message").path("content").asText();
            }
            return "AI 未返回有效内容";

        } catch (IOException e) {
            log.error("用户 AI 调用异常，用户: {}: {}", user.getOpenId(), e.getMessage(), e);
            return "AI 服务连接错误";
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 类型化调用：自动完成 JSON 清洗 + 反序列化
    // ─────────────────────────────────────────────────────────────────

    /**
     * 使用系统配置调用 AI，将 JSON 响应直接反序列化为目标类型。
     *
     * @param systemPrompt 系统提示词
     * @param userMessage  用户消息
     * @param responseType 期望的响应类型
     * @throws IOException 响应不合法 JSON 或无法映射时抛出
     */
    public <T> T chatForObject(String systemPrompt, String userMessage, Class<T> responseType) throws IOException {
        String raw = chat(systemPrompt, userMessage);
        return mapper.readValue(cleanJsonResponse(raw), responseType);
    }

    /**
     * 用户感知版：优先使用用户自定义配置，将 JSON 响应反序列化为目标类型。
     *
     * @param user         用户配置（为 null 或无自定义 Key 时退回系统配置）
     * @param systemPrompt 系统提示词
     * @param userMessage  用户消息
     * @param responseType 期望的响应类型
     * @throws IOException 响应不合法 JSON 或无法映射时抛出
     */
    public <T> T chatForObject(UserConfig user, String systemPrompt, String userMessage, Class<T> responseType) throws IOException {
        String raw = chat(user, systemPrompt, userMessage);
        return mapper.readValue(cleanJsonResponse(raw), responseType);
    }

    /**
     * 用户感知版：AI 响应为 JSON 数组时使用此方法。
     *
     * @param user        用户配置
     * @param systemPrompt 系统提示词
     * @param userMessage 用户消息
     * @param elementType 数组元素的类型
     * @throws IOException 响应不合法 JSON 或无法映射时抛出
     */
    public <T> List<T> chatForList(UserConfig user, String systemPrompt, String userMessage, Class<T> elementType) throws IOException {
        String raw = chat(user, systemPrompt, userMessage);
        return mapper.readValue(cleanJsonResponse(raw),
                mapper.getTypeFactory().constructCollectionType(List.class, elementType));
    }

    /**
     * 清洗 AI 响应中可能携带的 Markdown 代码块标记（{@code ```json ... ```}）。
     * 各服务在解析 JSON 前应统一调用此方法，而非各自重复实现清洗逻辑。
     *
     * @param raw AI 原始响应文本
     * @return 去除代码块标记后的纯 JSON 字符串
     */
    public static String cleanJsonResponse(String raw) {
        if (raw == null) return "{}";
        return raw.replaceAll("(?s)^```json\\s*", "")
                  .replaceAll("(?s)^```\\w*\\s*", "")
                  .replaceAll("(?s)\\s*```$", "")
                  .trim();
    }

    private Map<String, String> buildHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + apiKey);
        headers.put("Content-Type", "application/json");
        return headers;
    }
}
