package com.example.wxnotion.service;

import com.example.wxnotion.model.PromptConfig;
import com.example.wxnotion.model.UserConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 提示管理器
 * 负责组装 Prompt，实现“固定部分 + 可变部分”的动态组合策略。
 */
@Service
@Slf4j
public class PromptManager {

    private static final String FIXED_PART_1_ROLE = "生成一份“每日回响”日报。";

    private static final String FIXED_PART_2_CONSTRAINTS = """
            请直接返回标准 JSON 格式数据，不要包含 Markdown 标记。
            只返回 JSON，不要返回其他废话。
            """;

    // 默认的可变部分策略
    private static final String DEFAULT_SUMMARY_ROLE = "你是一个极具洞察力的私人生活助理，你的任务是阅读用户昨天一整天的碎片化笔记";

    private static final String DEFAULT_YESTERDAY_SUMMARY_1 = "用一段话精炼概括昨天发生的主要内容和亮点";
    private static final String DEFAULT_YESTERDAY_SUMMARY_2 = "字数 100 字以内";

    private static final String DEFAULT_EMOTION_WEATHER_1 = "分析情绪起伏，给出一个天气隐喻(如🌤️ 多云转晴)，简述原因。无明显情绪可为空字符串";
    private static final String DEFAULT_EMOTION_WEATHER_2 = "无明显情绪可为空字符串";

    private static final String DEFAULT_SUBCONSCIOUS_LINK_1 = "找出潜在联系或重复主题。";
    private static final String DEFAULT_SUBCONSCIOUS_LINK_2 = "无内容可为空字符串";

    private static final String DEFAULT_TODAY_QUOTE_1 = "基于昨天经历，从经典名著/诗句/动漫/影视剧/歌曲中找出一句符合场景的一句话并给出出处，";
    private static final String DEFAULT_TODAY_QUOTE_2 = "你需要做好断句给好换行";

    private static final String DEFAULT_KEYWORDS = "提取2-5个最能代表昨天的关键词,用空格分隔,如 #阅读 #冥想 #效率";

    /**
     * 组装完整的 System Prompt
     * @param userConfig 用户配置（包含自定义的 Prompt 策略）
     * @return 完整的 Prompt 字符串
     */
    public String assembleSystemPrompt(UserConfig userConfig) {
        PromptConfig promptConfig = userConfig.getPromptConfig();

        // 组装 JSON 结构描述 (如果用户配置为空，使用默认值)
        String summaryRole = Optional.ofNullable(promptConfig.getSummaryRole()).orElse(DEFAULT_SUMMARY_ROLE) + FIXED_PART_1_ROLE;
        StringBuilder jsonSchemaBuilder = new StringBuilder();
        jsonSchemaBuilder.append("{\n");

        appendField(jsonSchemaBuilder, "yesterday_summary", promptConfig.getYesterdaySummary(), DEFAULT_YESTERDAY_SUMMARY_1,  DEFAULT_YESTERDAY_SUMMARY_2);
        appendField(jsonSchemaBuilder, "emotion_weather", promptConfig.getEmotionWeather(), DEFAULT_EMOTION_WEATHER_1, DEFAULT_EMOTION_WEATHER_2);
        appendField(jsonSchemaBuilder, "subconscious_link", promptConfig.getSubconsciousLink(), DEFAULT_SUBCONSCIOUS_LINK_1, DEFAULT_SUBCONSCIOUS_LINK_2);
        appendField(jsonSchemaBuilder, "today_quote", promptConfig.getTodayQuote(), DEFAULT_TODAY_QUOTE_1, DEFAULT_TODAY_QUOTE_2);
        appendField(jsonSchemaBuilder, "keywords", promptConfig.getKeywords(), DEFAULT_KEYWORDS);

        // 移除最后一个逗号
        if (jsonSchemaBuilder.length() > 2) {
            jsonSchemaBuilder.setLength(jsonSchemaBuilder.length() - 2);
            jsonSchemaBuilder.append("\n");
        }
        jsonSchemaBuilder.append("}");

        return String.format("%s\n字段定义如下：\n%s\n\n%s",
                summaryRole,
                jsonSchemaBuilder,
                FIXED_PART_2_CONSTRAINTS);
    }

    private void appendField(StringBuilder sb, String key, String userValue, String defaultValue) {
        String value = (userValue != null && !userValue.isEmpty()) ? userValue : defaultValue;
        sb.append(String.format("  \"%s\": \"%s\",\n", key, value));
    }

    private void appendField(StringBuilder sb, String key, String userValue, String defaultValue1, String defaultValue2) {
        String value = (userValue != null && !userValue.isEmpty()) ? userValue + defaultValue2 : defaultValue1 + defaultValue2;
        sb.append(String.format("  \"%s\": \"%s\",\n", key, value));
    }
    
    /**
     * 获取默认配置对象
     */
    public PromptConfig getDefaultStrategies() {
        PromptConfig promptConfig = new PromptConfig();
        promptConfig.setSummaryRole(DEFAULT_SUMMARY_ROLE);
        promptConfig.setYesterdaySummary(DEFAULT_YESTERDAY_SUMMARY_1);
        promptConfig.setEmotionWeather(DEFAULT_EMOTION_WEATHER_1);
        promptConfig.setSubconsciousLink(DEFAULT_SUBCONSCIOUS_LINK_1);
        promptConfig.setTodayQuote(DEFAULT_TODAY_QUOTE_1);
        promptConfig.setKeywords(DEFAULT_KEYWORDS);
        return promptConfig;
    }
}
