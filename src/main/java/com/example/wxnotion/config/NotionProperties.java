package com.example.wxnotion.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "notion")
public class NotionProperties {
  /** Notion API 版本号（请求头 Notion-Version） */
  private String version;

  /** 管理员 Token (用于访客模式) */
  private String adminToken;

  /** 访客工作区根页面 ID */
  private String guestRootPageId;
}
