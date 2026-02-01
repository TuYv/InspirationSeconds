package com.example.wxnotion.service;

import com.alibaba.fastjson.JSON;
import com.example.wxnotion.mapper.UserConfigRepository;
import com.example.wxnotion.model.PromptConfig;
import com.example.wxnotion.model.UserConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * 提示优化服务
 * 负责执行“预检查”和“生成优化建议”逻辑。
 */
@Service
@Slf4j
public class PromptOptimizationService {

    @Autowired
    private AiService aiService;

    @Autowired
    private PromptManager promptManager;

    @Autowired
    private UserConfigRepository userConfigRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 元提示 (Meta-Prompt)：用于让 AI 分析笔记并提供优化建议
    private static final String OPTIMIZATION_META_PROMPT = """
            你是一个Prompt优化专家。你的任务是分析用户的“今日笔记”和当前的“日报生成策略”，判断是否需要调整策略以生成更高质量的日报。

            当前日报生成策略（JSON字段定义）：
            %s

            用户今日笔记：
            %s

            请按以下步骤思考：
            1. **显式指令检查**：用户笔记中是否有明确要求调整日报风格、内容侧重或格式的指令？（如“以后多写点感悟”、“今天的总结要幽默点”）
            2. **隐式契合度检查**：当前策略是否能充分挖掘今日笔记的价值？（例如：笔记全是读书摘抄，但策略只关注流水账；或笔记情绪波动大，但策略未强调情绪分析）
            3. **固定内容不可调整** 策略中包含被大括号即{}，包含的内容，该部分在不可变动，并且返回时需要依然保持被

            判断逻辑：
            - 如果命中显式指令 -> 必须优化。
            - 如果存在显著的隐式不契合 -> 建议优化。
            - 否则 -> 不优化。

            请返回标准 JSON 格式结果：
            {
              "needs_optimization": true/false,
              "reason": "简述优化的原因",
              "optimized_field_strategies": {
                 // 仅包含需要修改的字段及其新的描述指导语。字段名必须与原策略一致。
                 // 示例: "today_quote": "由于用户今天读了《三体》，请优先从《三体》中提取金句"
              }
            }
            """;

    /**
     * 执行 Prompt 优化流程
     * @param userConfig 用户配置
     * @param userNotes 用户今日笔记内容
     * @return 是否进行了优化
     */
    public boolean optimizePromptIfNecessary(UserConfig userConfig, String userNotes) {
        // 1. 组装当前的策略描述（作为上下文传给 AI）
        PromptConfig promptConfig = Optional.ofNullable(userConfig).map(UserConfig::getPromptConfig).orElse(promptManager.getDefaultStrategies());

        // 2. 构造 Meta-Prompt
        String systemPrompt = String.format(OPTIMIZATION_META_PROMPT, JSON.toJSONString(promptConfig), userNotes);

        try {
            // 3. 调用 AI 获取优化建议
            String aiResponse = aiService.chat(systemPrompt, "请分析并返回 JSON");
            
            // 清洗 Markdown 标记
            aiResponse = cleanJson(aiResponse);

            // 4. 解析 AI 返回的结果
            Map<String, Object> result = objectMapper.readValue(aiResponse, Map.class);
            boolean needsOptimization = (boolean) result.getOrDefault("needs_optimization", false);

            if (needsOptimization) {
                Map<String, String> optimizedStrategiesMap = (Map<String, String>) result.get("optimized_field_strategies");
                if (optimizedStrategiesMap != null && !optimizedStrategiesMap.isEmpty()) {
                    // 5. 应用优化：更新 UserConfig
                    applyOptimization(userConfig, optimizedStrategiesMap);
                    log.info("用户 {} 的 Prompt 已优化，原因: {}", userConfig.getOpenId(), result.get("reason"));
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("Prompt 优化流程失败", e);
        }
        return false;
    }
    
    private void applyOptimization(UserConfig userConfig, Map<String, String> optimizedStrategiesMap) {
        PromptConfig promptConfig = userConfig.getPromptConfig();

        if (promptConfig == null) {
            promptConfig = promptManager.getDefaultStrategies();
        }
        
        // 应用优化（手动映射，因为 Key 是字符串）
        if (optimizedStrategiesMap.containsKey("summary_role")) promptConfig.setSummaryRole(optimizedStrategiesMap.get("summary_role"));
        if (optimizedStrategiesMap.containsKey("yesterday_summary")) promptConfig.setYesterdaySummary(optimizedStrategiesMap.get("yesterday_summary"));
        if (optimizedStrategiesMap.containsKey("emotion_weather")) promptConfig.setEmotionWeather(optimizedStrategiesMap.get("emotion_weather"));
        if (optimizedStrategiesMap.containsKey("subconscious_link")) promptConfig.setSubconsciousLink(optimizedStrategiesMap.get("subconscious_link"));
        if (optimizedStrategiesMap.containsKey("today_quote")) promptConfig.setTodayQuote(optimizedStrategiesMap.get("today_quote"));
        if (optimizedStrategiesMap.containsKey("keywords")) promptConfig.setKeywords(optimizedStrategiesMap.get("keywords"));
        
        userConfig.setPromptConfig(promptConfig);
        
        // 保存到数据库
        userConfigRepository.updateById(userConfig);
    }

    private String cleanJson(String text) {
        if (text == null) return "{}";
        return text.replaceAll("```json", "").replaceAll("```", "").trim();
    }
}
