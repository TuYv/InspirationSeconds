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

  private final ConfigFlowService configFlowService;
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
   * 根据文本内容匹配指令或进入配置流程，否则执行同步。
   */
  public void handle(WxMpXmlMessage in) {
    String openId = in.getFromUser();
    String msgType = in.getMsgType();
    String content = StringUtils.defaultString(in.getContent(), "").trim();

    // 立即返回空响应，避免超过5秒超时
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
          case "text" -> processTextMessage(openId, content);
          case "image" -> processImageMessage(in, openId);
          default -> "暂不支持处理此类消息";
      };

      // 向用户推送处理结果(通过客服消息接口)
      wechatService.pushMessageToUser(openId, reply);
    } catch (Exception e) {
      log.error("异步处理消息失败，用户: {}", openId, e);
      // 出错时也推送错误信息给用户
      wechatService.pushMessageToUser(openId, "处理您的消息时出现错误，请稍后重试");
    }
  }

  /**
   * 处理文本消息
   */
  public String processTextMessage(String openId, String content) {

    if (content.startsWith("配置Notion") || content.startsWith("修改Notion配置")
     || content.startsWith("配置notion") || content.startsWith("修改notion配置")) {
      // 进入/重置配置流程
      return configFlowService.startOrReset(openId);
    } else if (content.startsWith("查询我的配置")) {
      // 查询当前配置状态
      return configFlowService.queryConfig(openId);
    } else {
      // 若当前处于配置流程，则继续处理；否则执行内容同步
      String flowReply = configFlowService.handleInput(openId, content);
      return flowReply != null ? flowReply : syncService.sync(openId, content);
    }
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
