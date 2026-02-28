package com.example.wxnotion.config;

import lombok.Data;
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
 * - oauthCallbackBase：OAuth 回调基地址（可选，覆盖自动推断）
 * - oauthReturnUrl：OAuth 回调后跳转的默认页面
 * - oauthScope：OAuth 授权范围（默认 snsapi_base）
 */
@Data
@Component
@ConfigurationProperties(prefix = "wx")
public class WxProperties {
  private String appId;
  private String secret;
  private String token;
  private String encodingAesKey;
  private String oauthCallbackBase;
  private String oauthReturnUrl;
  private String oauthScope;
}
