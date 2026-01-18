package com.example.wxnotion.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.wxnotion.mapper.UserConfigRepository;
import com.example.wxnotion.model.ConfigStatus;
import com.example.wxnotion.model.UserConfig;
import com.example.wxnotion.util.AesUtil;
import com.example.wxnotion.util.ContentUtil;
import com.example.wxnotion.util.TagUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SyncService {
  private final UserConfigRepository configRepo;
  private final NotionService notionService;

  @Value("${security.aesKey}")
  private String aesKey;

  /**
   * 将用户文本同步至 Notion。
   *
   * 步骤：
   * 1. 检查用户配置并解密 API Key
   * 2. 解析正文与标签
   * 3. 智能拆分标题与正文
   * 4. 调用 Notion 创建页面，并返回结果文案
   */
  public String sync(String openId, String content) {
    UserConfig cfg = configRepo.selectOne(new QueryWrapper<UserConfig>().eq("open_id", openId));
    if (cfg == null || cfg.getStatus() != ConfigStatus.ACTIVE) {
      return "尚未完成配置或配置未启用。发送‘配置Notion’开始配置";
    }
    
    ContentUtil.NotionContent notionContent = ContentUtil.trans(content);

    String apiKey = AesUtil.decrypt(aesKey, cfg.getEncryptedApiKey());
    try {
      NotionService.CreateResult result = notionService.createPage(apiKey, cfg.getDatabaseId(),notionContent);
      if (result.ok) {
        String msg = "同步成功。\n标题：" + notionContent.getTitle();
        if (!notionContent.getTags().isEmpty()) {
            msg += "\n标签：" + String.join(", ", notionContent.getTags());
        }
        return msg;
      } else {
        return "同步失败。请检查数据库配置或稍后重试";
      }
    } catch (Exception e) {
      // 将异常信息反馈给用户，便于排查
      return "同步异常：" + e.getMessage();
    }
  }
}
