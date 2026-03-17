package com.example.wxnotion.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.api.WxConsts;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.bean.kefu.WxMpKefuMessage;
import me.chanjar.weixin.mp.bean.message.WxMpXmlMessage;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import me.chanjar.weixin.common.bean.result.WxMediaUploadResult;
import java.io.File;

@Slf4j
@Service
@RequiredArgsConstructor
public class HandlerWxPortalService implements ApplicationContextAware {

  private static final String WEB_CONFIG_URL = "https://wx.soloship.top";

  private final SyncService syncService;
  private final WxMpService wxMpService;
  private final WechatService wechatService;
  private HandlerWxPortalService proxyInstance;
  private ApplicationContext applicationContext;

  private HandlerWxPortalService getThis() {
    if (proxyInstance == null) {
      proxyInstance = applicationContext.getBean(HandlerWxPortalService.class);
    }
    return proxyInstance;
  }

  /**
   * 微信消息处理入口。
   * 事件消息（关注等）直接处理，其他消息异步同步到 Notion。
   */
  public void handle(WxMpXmlMessage in) {
    String openId = in.getFromUser();
    String msgType = in.getMsgType();
    String content = StringUtils.defaultString(in.getContent(), "").trim();

    log.info("接收到微信消息，开始异步处理。用户: {}, 消息类型: {}", openId, msgType);

    this.getThis().processMessageAsync(in, openId, msgType, content);
  }

  /**
   * 异步处理消息
   */
  @Async
  public void processMessageAsync(WxMpXmlMessage in, String openId, String msgType, String content) {
    log.info("异步处理消息，用户: {}, 消息类型: {}", openId, msgType);

    try {
      String reply = switch (msgType) {
          case "event" -> processEvent(in, openId);
          case "text" -> syncService.sync(openId, content);
          case "image" -> processImageMessage(in, openId);
          default -> "暂不支持处理此类消息";
      };

      if (reply != null) {
        wechatService.pushMessageToUser(openId, reply);
      }
    } catch (Exception e) {
      log.error("异步处理消息失败，用户: {}", openId, e);
      wechatService.pushMessageToUser(openId, "处理您的消息时出现错误，请稍后重试");
    }
  }

  /**
   * 处理微信事件（关注、取消关注等）
   */
  private String processEvent(WxMpXmlMessage in, String openId) {
    String event = StringUtils.defaultString(in.getEvent(), "");
    return switch (event.toLowerCase()) {
      case "subscribe" -> {
        log.info("新用户关注: {}", openId);
        yield "欢迎关注灵感妙记！\n\n"
            + "在这里发送的文字会自动同步到你的 Notion 笔记本。\n\n"
            + "首次使用请先完成配置：\n"
            + WEB_CONFIG_URL + "\n\n"
            + "配置完成后，直接发消息就能记录啦。";
      }
      case "unsubscribe" -> {
        log.info("用户取消关注: {}", openId);
        yield null;
      }
      default -> {
        log.info("未处理的事件类型: {}, 用户: {}", event, openId);
        yield null;
      }
    };
  }

  /**
   * 处理图片消息
   */
  private String processImageMessage(WxMpXmlMessage in, String openId)   {
    // 获取图片的 MediaId 和 PicUrl
    String mediaId = in.getMediaId();
    String picUrl = in.getPicUrl();

    log.info("收到图片消息，MediaId: {}, PicUrl: {}, 用户: {}", mediaId, picUrl, openId);

    // 目前暂时回复提示信息，告知用户图片已收到但暂不处理
    return "已收到您的图片，但目前暂不支持图片同步到Notion。请使用文字消息同步笔记。";
  }

  /**
   * 推送图片给用户（客服消息）
   */
  public void pushImageToUser(String openId, File imageFile) {
      try {
          // 1. 上传图片到微信服务器 (获得 media_id)
          // "image" 是微信规定的媒体类型
          WxMediaUploadResult uploadResult = wxMpService.getMaterialService().mediaUpload(WxConsts.MediaFileType.IMAGE, imageFile);
          String mediaId = uploadResult.getMediaId();

          // 2. 构建图片客服消息
          WxMpKefuMessage kefuMsg = WxMpKefuMessage.IMAGE()
                  .toUser(openId)
                  .mediaId(mediaId)
                  .build();

          // 3. 发送
          wxMpService.getKefuService().sendKefuMessage(kefuMsg);
          
          log.info("图片已推送给用户: {}, MediaId: {}", openId, mediaId);

      } catch (Exception e) {
          log.error("推送图片给用户失败: {}", e.getMessage(), e);
          wechatService.pushMessageToUser(openId, "日签图片生成失败，请稍后重试");
      }
  }

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
    this.applicationContext = applicationContext;
  }
}
