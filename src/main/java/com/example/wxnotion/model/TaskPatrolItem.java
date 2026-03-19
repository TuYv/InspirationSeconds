package com.example.wxnotion.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * AI 任务巡检结果中的单条提醒项。
 * <pre>
 * {"task_id": "...", "task_name": "...", "remind_message": "...", "progress": "..."}
 * </pre>
 * AI 返回的是此类的 JSON 数组。
 */
public class TaskPatrolItem {

    @JsonProperty("task_id")
    public String taskId;

    @JsonProperty("task_name")
    public String taskName;

    @JsonProperty("remind_message")
    public String remindMessage;

    public String progress;
}
