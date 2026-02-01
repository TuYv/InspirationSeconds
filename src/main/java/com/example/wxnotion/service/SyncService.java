package com.example.wxnotion.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.wxnotion.config.NotionProperties;
import com.example.wxnotion.mapper.UserConfigRepository;
import com.example.wxnotion.model.ConfigStatus;
import com.example.wxnotion.model.UserConfig;
import com.example.wxnotion.util.AesUtil;
import com.example.wxnotion.util.ContentUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Slf4j
public class SyncService {
  private final UserConfigRepository configRepo;
  private final NotionService notionService;
  private final NotionProperties notionProperties;

  @Value("${security.aesKey}")
  private String aesKey;

  /**
   * 将用户文本同步至 Notion。
   */
  public String sync(String openId, String content) {
    UserConfig cfg = configRepo.selectOne(new QueryWrapper<UserConfig>().eq("open_id", openId));
    
    // 0. 自动初始化访客 (如果配置为空)
    if (cfg == null) {
        cfg = initGuestUser(openId);
        if (cfg == null) return "系统初始化访客失败，请稍后重试";
    }

    // 1. 检查迁移状态
    if ("MIGRATING".equals(cfg.getMigrationStatus())) {
        return "您的数据正在迁移中，请稍候再试...";
    }
    
    // 2. 检查配置状态 (非访客必须 ACTIVE)
    if (!Boolean.TRUE.equals(cfg.getIsGuest()) && cfg.getStatus() != ConfigStatus.ACTIVE) {
      return "尚未完成配置或配置未启用。发送‘配置Notion’开始配置";
    }
    
    // 3. 确定 API Key 和 Database ID
    String apiKey;
    String databaseId = cfg.getDatabaseId();
    
    if (Boolean.TRUE.equals(cfg.getIsGuest())) {
        // 访客模式：使用 Admin Token
        apiKey = notionProperties.getAdminToken();
        // 如果 databaseId 为空，尝试重新创建 (容错)
        if (databaseId == null) {
            databaseId = notionService.createDatabase(apiKey, notionProperties.getGuestRootPageId(), "NoteBox_" + openId.substring(Math.max(0, openId.length() - 6)));
            if (databaseId != null) {
                cfg.setDatabaseId(databaseId);
                configRepo.updateById(cfg);
            } else {
                return "创建访客笔记本失败，请联系管理员";
            }
        }
    } else {
        // 正式模式：解密用户 Key
        apiKey = AesUtil.decrypt(aesKey, cfg.getEncryptedApiKey());
    }
    
    ContentUtil.NotionContent notionContent = ContentUtil.trans(content);
    
    try {
      // 4. 查询今日页面
      String todayPageId = notionService.findTodayPage(apiKey, databaseId);
      
      if (todayPageId != null) {
        // 5. 追加到今日页面
        boolean ok = notionService.appendContent(apiKey, todayPageId, notionContent);
        if (ok) {
          return "已追加到今日笔记。\n摘要：" + notionContent.getTitle();
        } else {
          return "追加今日笔记失败，请重试";
        }
      } else {
        // 6. 创建今日新页面 (覆盖 Title 为日期)
        String todayStr = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
        notionContent.setContent(notionContent.getTitle() + "\n" + notionContent.getContent());
        notionContent.setTitle(todayStr);

        NotionService.CreateResult result = notionService.createPage(apiKey, databaseId, notionContent);
        if (result.ok) {
          return "今日笔记已创建。\n日期：" + todayStr;
        } else {
          log.error("创建页面失败: {}", result.raw);
          return "创建今日笔记失败。请检查数据库配置";
        }
      }
    } catch (Exception e) {
      log.error("同步异常", e);
      return "同步异常：" + e.getMessage();
    }
  }

  private UserConfig initGuestUser(String openId) {
      // 1. 创建 UserConfig
      UserConfig user = new UserConfig();
      user.setOpenId(openId);
      user.setAppType(com.example.wxnotion.model.NoteAppType.NOTION);
      user.setStatus(ConfigStatus.ACTIVE); // Guest 默认 Active
      user.setIsGuest(true);
      user.setMigrationStatus("NONE");
      // 修正: 即使是 Guest，encryptedApiKey 也不能为 NULL (数据库约束)
      // 填入一个占位符或空字符串
      user.setEncryptedApiKey("GUEST_MODE_PLACEHOLDER");
      user.setUpdatedAt(LocalDateTime.now());
      
      // 2. 动态创建 Database
      String apiKey = notionProperties.getAdminToken();
      String rootPageId = notionProperties.getGuestRootPageId();
      if (apiKey != null && rootPageId != null) {
          String suffix = openId.length() > 6 ? openId.substring(openId.length() - 6) : openId;
          String dbId = notionService.createDatabase(apiKey, rootPageId, "NoteBox_" + suffix);
          user.setDatabaseId(dbId);
      }
      
      try {
          configRepo.insert(user);
          return user;
      } catch (Exception e) {
          // 不要吞掉异常，至少让上层知道发生了什么，或者在这里抛出运行时异常
          log.error("初始化访客数据库插入失败", e);
          throw new RuntimeException("初始化访客失败: " + e.getMessage(), e);
      }
  }
}
