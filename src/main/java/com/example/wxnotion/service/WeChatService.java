package com.example.wxnotion.service;

import me.chanjar.weixin.common.api.WxConsts;
import me.chanjar.weixin.mp.bean.message.WxMpXmlMessage;
import me.chanjar.weixin.mp.bean.message.WxMpXmlOutMessage;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class WeChatService {
  private final ConfigFlowService configFlowService;
  private final SyncService syncService;

  public WeChatService(ConfigFlowService configFlowService, SyncService syncService) {
    this.configFlowService = configFlowService;
    this.syncService = syncService;
  }

  /**
   * 微信消息处理入口。
   *
   * 根据文本内容匹配指令或进入配置流程，否则执行同步。
   */
  public WxMpXmlOutMessage handle(WxMpXmlMessage in) {
    String openId = in.getFromUser();
    String content = StringUtils.defaultString(in.getContent(), "").trim();

    String reply;
    if ("配置Notion".equalsIgnoreCase(content) || "修改Notion配置".equalsIgnoreCase(content)) {
      // 进入/重置配置流程
      reply = configFlowService.startOrReset(openId);
    } else if ("查询我的配置".equalsIgnoreCase(content)) {
      // 查询当前配置状态
      reply = configFlowService.queryConfig(openId);
    } else {
      // 若当前处于配置流程，则继续处理；否则执行内容同步
      String flowReply = configFlowService.handleInput(openId, content);
      reply = flowReply != null ? flowReply : syncService.sync(openId, content);
    }

    return WxMpXmlOutMessage.TEXT()
        .fromUser(in.getToUser())
        .toUser(openId)
        .content(reply)
        .build();
  }
}
