package com.example.wxnotion.config;

import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.api.impl.WxMpServiceImpl;
import me.chanjar.weixin.mp.config.WxMpConfigStorage;
import me.chanjar.weixin.mp.config.impl.WxMpDefaultConfigImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WxConfig {
  /**
   * 构建 WxJava 的配置存储，注入 appId/secret/token。
   *
   * WxJava 使用该对象完成签名校验与接口调用。
   */
  @Bean
  public WxMpConfigStorage wxMpConfigStorage(WxProperties props) {
    WxMpDefaultConfigImpl config = new WxMpDefaultConfigImpl();
    config.setAppId(props.getAppId());
    config.setSecret(props.getSecret());
    config.setToken(props.getToken());
    if (props.getEncodingAesKey() != null && !props.getEncodingAesKey().isEmpty()) {
      config.setAesKey(props.getEncodingAesKey());
    }
    return config;
  }

  /**
   * 构建 WxMpService 服务实例，绑定配置存储。
   */
  @Bean
  public WxMpService wxMpService(WxMpConfigStorage storage) {
    WxMpServiceImpl service = new WxMpServiceImpl();
    service.setWxMpConfigStorage(storage);
    return service;
  }
}
