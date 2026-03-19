package com.example.wxnotion.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * AI 任务终结意图识别结果。
 * <pre>
 * {
 *   "is_termination": true/false,
 *   "status": "completed" | "abandoned" | "deleted" | null,
 *   "task_page_id": "...",
 *   "task_name": "..."
 * }
 * </pre>
 * {@code status} / {@code taskPageId} / {@code taskName} 在 {@code isTermination=false} 时均为 null。
 */
public class TaskTerminationResult {

    @JsonProperty("is_termination")
    public boolean isTermination = false;

    /** "completed" / "abandoned" / "deleted" / null */
    public String status;

    @JsonProperty("task_page_id")
    public String taskPageId;

    @JsonProperty("task_name")
    public String taskName;
}
