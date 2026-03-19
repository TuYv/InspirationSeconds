package com.example.wxnotion.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务草稿已收集的字段数据。
 * 作为 {@link TaskDraft#draftJson} 的类型，存储在数据库 JSON 列中。
 *
 * <p>{@code @JsonProperty} 保持与数据库中已有 snake_case 键名兼容。
 */
@Data
public class TaskDraftData {

    /** recurring / one_time */
    @JsonProperty("task_type")
    private String taskType;

    private String name;

    /** recurring 任务的周期描述，如"每天" */
    private String cycle;

    /** one_time 任务的触发背景 */
    private String trigger;

    /** one_time 任务的当前进度 */
    @JsonProperty("current_progress")
    private String currentProgress;

    /** one_time 任务的完成标准 */
    @JsonProperty("end_condition")
    private String endCondition;

    /** 尚未收集的字段名列表 */
    @JsonProperty("missing_fields")
    private List<String> missingFields = new ArrayList<>();
}
