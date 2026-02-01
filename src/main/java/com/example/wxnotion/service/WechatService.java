package com.example.wxnotion.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.api.WxConsts;
import me.chanjar.weixin.common.bean.result.WxMediaUploadResult;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.bean.kefu.WxMpKefuMessage;
import org.springframework.stereotype.Service;

import java.io.File;

/**
 * @author 瑞克
 * @date 2026/2/1 15:05
 * @description
 */
@Slf4j
@Service
@AllArgsConstructor
public class WechatService {

    private final WxMpService wxMpService;

    /**
     * 推送消息给用户（使用客服消息接口）
     */
    public void pushMessageToUser(String openId, String content) {
        try {
            // 使用客服消息接口向用户推送消息
            WxMpKefuMessage kefuMsg = WxMpKefuMessage.TEXT()
                    .toUser(openId)
                    .content(content)
                    .build();
            wxMpService.getKefuService().sendKefuMessage(kefuMsg);

            log.info("消息已推送给用户: {}, 内容: {}", openId, content);

        } catch (Exception e) {
            log.error("推送消息给用户失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 获取用户头像 URL
     */
    public String getUserAvatarUrl(String openId) {
        try {
            return wxMpService.getUserService().userInfo(openId).getHeadImgUrl();
        } catch (Exception e) {
            log.warn("获取用户头像失败: {}", e.getMessage());
            return null;
        }
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
            pushMessageToUser(openId, "日签图片生成失败，请稍后重试");
        }
    }
}
