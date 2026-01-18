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

import java.time.LocalDate;
import java.time.ZoneId;

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
   * 3. 查询今天是否已有页面：
   *    - 有：追加内容 (Title作为小标题)
   *    - 无：创建新页面 (Title为当前日期)
   */
  public String sync(String openId, String content) {
    UserConfig cfg = configRepo.selectOne(new QueryWrapper<UserConfig>().eq("open_id", openId));
    if (cfg == null || cfg.getStatus() != ConfigStatus.ACTIVE) {
      return "尚未完成配置或配置未启用。发送‘配置Notion’开始配置";
    }
    
    ContentUtil.NotionContent notionContent = ContentUtil.trans(content);
    String apiKey = AesUtil.decrypt(aesKey, cfg.getEncryptedApiKey());
    
    try {
      // 1. 查询今日页面
      String todayPageId = notionService.findTodayPage(apiKey, cfg.getDatabaseId());
      
      if (todayPageId != null) {
        // 2. 追加到今日页面
        boolean ok = notionService.appendContent(apiKey, todayPageId, notionContent);
        if (ok) {
          return "已追加到今日笔记。\n摘要：" + notionContent.getTitle();
        } else {
          return "追加今日笔记失败，请重试";
        }
      } else {
        // 3. 创建今日新页面 (覆盖 Title 为日期)
        String todayStr = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
        // 保留原内容作为正文的一部分，但 Page Title 设为日期
        // 为了不丢失解析出的 Title（原消息第一行），我们需要微调 content
        // 但 createPage 内部使用了 content.getTitle() 作为 Page Title
        // 所以我们这里构造一个新的 NotionContent
        ContentUtil.NotionContent dailyContent = new ContentUtil.NotionContent();
        dailyContent.setTitle(todayStr); // 页面标题：2026-01-18
        // 正文 = 原标题(第一行) + 原正文
        String fullBody = "";
        if (notionContent.getTitle() != null && !notionContent.getTitle().isEmpty()) {
            fullBody += "**" + notionContent.getTitle() + "**\n";
        }
        if (notionContent.getContent() != null) {
            fullBody += notionContent.getContent();
        }
        dailyContent.setContent(fullBody);
        dailyContent.setTags(notionContent.getTags());
        
        NotionService.CreateResult result = notionService.createPage(apiKey, cfg.getDatabaseId(), dailyContent);
        if (result.ok) {
          return "今日笔记已创建。\n日期：" + todayStr + "\n内容：" + notionContent.getTitle();
        } else {
          return "创建今日笔记失败。请检查数据库配置";
        }
      }
    } catch (Exception e) {
      return "同步异常：" + e.getMessage();
    }
  }
}
