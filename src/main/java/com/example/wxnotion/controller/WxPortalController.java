package com.example.wxnotion.controller;

import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.bean.message.WxMpXmlMessage;
import me.chanjar.weixin.mp.bean.message.WxMpXmlOutMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import com.example.wxnotion.service.WeChatService;
import org.apache.commons.lang3.StringUtils;

import java.util.Optional;

@RestController
@RequestMapping("/wx/portal")
@RequiredArgsConstructor
public class WxPortalController {
  private final WxMpService wxService;
  private final WeChatService weChatService;

  /**
   * 微信服务器验证：原样返回 `echostr` 表示验证通过。
   *
   * 使用 WxJava 的签名校验：基于 timestamp/nonce/token 计算签名。
   */
  @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
  public String auth(@RequestParam("signature") String signature,
                     @RequestParam("timestamp") String timestamp,
                     @RequestParam("nonce") String nonce,
                     @RequestParam("echostr") String echostr) {
    boolean ok = wxService.checkSignature(timestamp, nonce, signature);
    return ok ? echostr : "";
  }

  /**
   * 接收微信推送的消息（XML）。
   *
   * 验证签名后解析 XML 文本消息并返回被动回复。
   */
  @PostMapping(produces = MediaType.APPLICATION_XML_VALUE)
  public String receive(@RequestParam("signature") String signature,
                        @RequestParam("timestamp") String timestamp,
                        @RequestParam("nonce") String nonce,
                        @RequestParam(value = "msg_signature", required = false) String msgSignature,
                        @RequestBody String xml) {
    if (!wxService.checkSignature(timestamp, nonce, signature)) {
      // 非法请求直接返回空串
      return "";
    }
    WxMpXmlMessage inMessage = StringUtils.isNotBlank(msgSignature)
        ? WxMpXmlMessage.fromEncryptedXml(xml, wxService.getWxMpConfigStorage(), timestamp, nonce, msgSignature)
        : WxMpXmlMessage.fromXml(xml);
    weChatService.handle(inMessage);
    return null;
  }
}
