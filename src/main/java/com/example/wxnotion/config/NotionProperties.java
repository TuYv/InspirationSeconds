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
}
