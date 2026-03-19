package com.example.wxnotion.service;

import com.alibaba.fastjson.JSON;
import com.example.wxnotion.mapper.UserConfigRepository;
import com.example.wxnotion.model.PromptConfig;
import com.example.wxnotion.model.PromptOptimizationResult;
import com.example.wxnotion.model.UserConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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


    /**
     * 执行 Prompt 优化流程
     * @param userConfig 用户配置
     * @param userNotes 用户今日笔记内容
     * @return 是否进行了优化
     */
    public boolean optimizePromptIfNecessary(UserConfig userConfig, String userNotes) {
        // 1. 组装当前的策略描述（作为上下文传给 AI）
        PromptConfig promptConfig = Optional.ofNullable(userConfig).map(UserConfig::getPromptConfig).orElse(promptManager.getDefaultStrategies());

        // 2. 构造并调用 Meta-Prompt
        String systemPrompt = AiPrompts.buildOptimizationMetaPrompt(JSON.toJSONString(promptConfig), userNotes);

        try {
            // 3. 调用 AI 并直接反序列化为结构化对象
            PromptOptimizationResult result = aiService.chatForObject(systemPrompt, "请分析并返回 JSON", PromptOptimizationResult.class);

            if (result.needsOptimization && result.optimizedFieldStrategies != null && !result.optimizedFieldStrategies.isEmpty()) {
                // 4. 应用优化：更新 UserConfig
                applyOptimization(userConfig, result.optimizedFieldStrategies);
                log.info("用户 {} 的 Prompt 已优化，原因: {}", userConfig.getOpenId(), result.reason);
                return true;
            }
        } catch (Exception e) {
            log.error("Prompt 优化流程失败", e);
        }
        return false;
    }
    
    private void applyOptimization(UserConfig userConfig, java.util.Map<String, String> optimizedStrategiesMap) {
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

}
