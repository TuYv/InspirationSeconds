package com.example.wxnotion.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 用户配置实体。
 *
 * - openId：微信用户唯一标识
 * - appType：笔记软件类型（当前支持 Notion）
 * - status：配置状态（启用/停用）
 * - encryptedApiKey：AES 加密后的 Notion API Key
 * - databaseId：Notion 数据库ID
 * - promptConfig：用户的自定义Prompt配置（JSON）
 * - updatedAt：最后更新时间
 */
@Data
@TableName(value = "user_config", autoResultMap = true)
public class UserConfig {
  @TableId(type = IdType.AUTO)
  private Long id;
  private String openId;
  private NoteAppType appType;
  private ConfigStatus status;
  private String encryptedApiKey;
  private String databaseId;
  
  @TableField(typeHandler = JacksonTypeHandler.class)
  private PromptConfig promptConfig;
  
  /**
   * 是否为访客用户
   */
  private Boolean isGuest;

  /**
   * 迁移状态: NONE, MIGRATING, DONE, FAILED
   */
  private String migrationStatus;
  
  private LocalDateTime updatedAt;
}
