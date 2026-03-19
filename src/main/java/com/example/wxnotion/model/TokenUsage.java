package com.example.wxnotion.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

/**
 * 用户每日 AI token 用量聚合记录。
 * 唯一键：(open_id, usage_date)。仅在用户使用自有 AI Key 时写入。
 */
@Data
@TableName("token_usage")
public class TokenUsage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String openId;

    private LocalDate usageDate;

    private int promptTokens;

    private int completionTokens;

    private int totalTokens;

    private int callCount;
}
