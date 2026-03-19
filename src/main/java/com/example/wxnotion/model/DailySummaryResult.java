package com.example.wxnotion.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * AI 日报分析结果，对应 PromptManager 中定义的日报 JSON 字段。
 * <pre>
 * {
 *   "yesterday_summary": "...",
 *   "emotion_weather": "...",
 *   "subconscious_link": "...",
 *   "today_quote": "...",
 *   "keywords": "..."
 * }
 * </pre>
 */
public class DailySummaryResult {

    @JsonProperty("yesterday_summary")
    public String yesterdaySummary;

    @JsonProperty("emotion_weather")
    public String emotionWeather;

    @JsonProperty("subconscious_link")
    public String subconsciousLink;

    @JsonProperty("today_quote")
    public String todayQuote;

    public String keywords;

    /**
     * 转换为 Notion Description 字段用的 Markdown 格式。
     */
    public String toMarkdown() {
        StringBuilder sb = new StringBuilder();
        if (yesterdaySummary != null) sb.append("📝 昨日回响\n").append(yesterdaySummary).append("\n\n");
        if (emotionWeather != null) sb.append("🎭 情绪气象台\n").append(emotionWeather).append("\n\n");
        if (subconsciousLink != null) sb.append("💡 潜意识连接\n").append(subconsciousLink).append("\n\n");
        if (todayQuote != null) sb.append("🔮 今日启示\n").append(todayQuote).append("\n\n");
        if (keywords != null) sb.append("🏷️ 关键词\n").append(keywords);
        return sb.toString();
    }
}
