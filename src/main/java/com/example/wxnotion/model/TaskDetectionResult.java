package com.example.wxnotion.model;

import lombok.Data;

import java.util.List;

/**
 * AI 任务检测结果 DTO。
 */
@Data
public class TaskDetectionResult {

    /** 是否为任务 */
    private boolean isTask;

    /** 任务类型：recurring（周期）/ one_time（一次性） */
    private String taskType;

    /** 已提取的字段 */
    private Extracted extracted;

    /** 缺失字段列表（需要追问） */
    private List<String> missingFields;

    /**
     * 可能关联的现有任务 ID（本阶段仅记录，不处理进度更新）
     */
    private String relatedTaskId;

    @Data
    public static class Extracted {
        private String name;
        private String cycle;
        private String trigger;
        private String currentProgress;
        private String endCondition;
    }
}
