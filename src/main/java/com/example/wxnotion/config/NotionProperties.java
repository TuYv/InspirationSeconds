package com.example.wxnotion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "notion")
public class NotionProperties {
  /** Notion API 版本号（请求头 Notion-Version） */
  private String version;
  public String getVersion() { return version; }
  public void setVersion(String version) { this.version = version; }
}
