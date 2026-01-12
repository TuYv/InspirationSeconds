package com.example.wxnotion.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 会话临时状态。
 *
 * 在分步配置流程中临时保存用户输入与当前步骤。
 */
@TableName("conversation_state")
public class ConversationState {
  @TableId(type = IdType.AUTO)
  private Long id;
  private String openId;
  private ConfigStep step;
  private String tempApiKey;
  private LocalDateTime updatedAt;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public String getOpenId() { return openId; }
  public void setOpenId(String openId) { this.openId = openId; }
  public ConfigStep getStep() { return step; }
  public void setStep(ConfigStep step) { this.step = step; }
  public String getTempApiKey() { return tempApiKey; }
  public void setTempApiKey(String tempApiKey) { this.tempApiKey = tempApiKey; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
