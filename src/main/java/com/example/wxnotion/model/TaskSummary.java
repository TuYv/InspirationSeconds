package com.example.wxnotion.model;

import lombok.Data;

/**
 * Active 任务的精简摘要，用于向 AI 传递当前任务列表。
 * 替代原先的 {@code Map<String, Object>} 写法。
 */
@Data
public class TaskSummary {

    private String id;

    private String name;

    /** recurring / one_time */
    private String type;
}
