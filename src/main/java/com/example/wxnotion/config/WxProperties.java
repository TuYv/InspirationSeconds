package com.example.wxnotion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信配置属性。
 *
 * 从 application.yml 加载 `wx.*` 前缀的配置：
 * - appId：公众号 AppID
 * - secret：公众号 AppSecret
 * - token：服务器校验用 Token
 * - encodingAesKey：消息加解密密钥（兼容/安全模式）
 */
@Component
@ConfigurationProperties(prefix = "wx")
public class WxProperties {
  private String appId;
  private String secret;
  private String token;
  private String encodingAesKey;

  public String getAppId() { return appId; }
  public void setAppId(String appId) { this.appId = appId; }
  public String getSecret() { return secret; }
  public void setSecret(String secret) { this.secret = secret; }
  public String getToken() { return token; }
  public void setToken(String token) { this.token = token; }
  public String getEncodingAesKey() { return encodingAesKey; }
  public void setEncodingAesKey(String encodingAesKey) { this.encodingAesKey = encodingAesKey; }
}
