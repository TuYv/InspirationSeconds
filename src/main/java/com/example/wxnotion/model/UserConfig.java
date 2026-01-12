package com.example.wxnotion.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 用户配置实体。
 *
 * - openId：微信用户唯一标识
 * - appType：笔记软件类型（当前支持 Notion）
 * - status：配置状态（启用/停用）
 * - encryptedApiKey：AES 加密后的 Notion API Key
 * - databaseId：Notion 数据库ID
 * - updatedAt：最后更新时间
 */
@TableName("user_config")
public class UserConfig {
  @TableId(type = IdType.AUTO)
  private Long id;
  private String openId;
  private NoteAppType appType;
  private ConfigStatus status;
  private String encryptedApiKey;
  private String databaseId;
  private LocalDateTime updatedAt;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public String getOpenId() { return openId; }
  public void setOpenId(String openId) { this.openId = openId; }
  public NoteAppType getAppType() { return appType; }
  public void setAppType(NoteAppType appType) { this.appType = appType; }
  public ConfigStatus getStatus() { return status; }
  public void setStatus(ConfigStatus status) { this.status = status; }
  public String getEncryptedApiKey() { return encryptedApiKey; }
  public void setEncryptedApiKey(String encryptedApiKey) { this.encryptedApiKey = encryptedApiKey; }
  public String getDatabaseId() { return databaseId; }
  public void setDatabaseId(String databaseId) { this.databaseId = databaseId; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
