package com.example.wxnotion.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * AI Prompt 优化分析结果。
 * <pre>
 * {
 *   "needs_optimization": true/false,
 *   "reason": "...",
 *   "optimized_field_strategies": {"field_name": "新的指导语"}
 * }
 * </pre>
 * {@code optimizedFieldStrategies} 仅包含需要修改的字段，字段名与 {@link PromptConfig} 属性名一致。
 */
public class PromptOptimizationResult {

    @JsonProperty("needs_optimization")
    public boolean needsOptimization;

    public String reason;

    @JsonProperty("optimized_field_strategies")
    public Map<String, String> optimizedFieldStrategies;
}
