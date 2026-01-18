package com.example.wxnotion.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话临时状态。
 *
 * 在分步配置流程中临时保存用户输入与当前步骤。
 */
@Data
@TableName("conversation_state")
public class ConversationState {
  @TableId(type = IdType.AUTO)
  private Long id;
  private String openId;
  private ConfigStep step;
  private String tempApiKey;
  private LocalDateTime updatedAt;
}
