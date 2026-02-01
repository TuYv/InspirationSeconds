package com.example.wxnotion.model;

import com.alibaba.fastjson.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 用户 Prompt 配置结构体
 * 对应数据库 user_config 表中的 prompt_config JSON 字段
 */
@Data
public class PromptConfig {

    @JsonProperty("summary_role")
    @JSONField(name = "summary_role")
    private String summaryRole;

    @JsonProperty("yesterday_summary")
    @JSONField(name = "yesterday_summary")
    private String yesterdaySummary;

    @JsonProperty("emotion_weather")
    @JSONField(name = "emotion_weather")
    private String emotionWeather;

    @JsonProperty("subconscious_link")
    @JSONField(name = "subconscious_link")
    private String subconsciousLink;

    @JsonProperty("today_quote")
    @JSONField(name = "today_quote")
    private String todayQuote;

    @JsonProperty("keywords")
    @JSONField(name = "keywords")
    private String keywords;
}
