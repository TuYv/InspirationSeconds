package com.example.wxnotion.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务草稿实体。
 * 追问完成前暂存用户任务信息和对话历史。
 */
@Data
@TableName(value = "task_draft", autoResultMap = true)
public class TaskDraft {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String openId;

    /**
     * 已收集的任务字段 + 待收集字段列表。
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private TaskDraftData draftJson;

    /**
     * 追问阶段的对话历史。
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<ConversationEntry> conversationLog;

    /**
     * 草稿状态：PENDING（追问中）/ EXPIRED（超时废弃）
     */
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
