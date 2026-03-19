package com.example.wxnotion.service;

import com.example.wxnotion.config.NotionProperties;
import com.example.wxnotion.mapper.UserConfigRepository;
import com.example.wxnotion.model.DailySummaryResult;
import com.example.wxnotion.model.UserConfig;
import com.example.wxnotion.util.AesUtil;
import com.example.wxnotion.util.BlockContentParser;
import com.example.wxnotion.util.ImageGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailySummaryService {

    private final UserConfigRepository userConfigRepository;
    private final NotionService notionService;
    private final AiService aiService;
    private final WechatService weChatService;
    private final WeeklySummaryService weeklySummaryService;
    private final PromptOptimizationService promptOptimizationService;
    private final PromptManager promptManager;
    private final NotionProperties notionProperties;

    @Value("${security.aesKey}")
    private String aesKey;

    /**
     * 每天 08:00 自动执行总结任务
     * 总结的是前一天的内容
     * 如果今天是周一，还会额外触发周报生成
     */
    @Scheduled(cron = "0 0 8 * * ?")
    public void generateDailySummaries() {
        log.info("开始执行每日 AI 总结任务...");
        List<UserConfig> users = userConfigRepository.selectActiveUsers();

        // 1. 生成日报
        for (UserConfig user : users) {
            try {
                // 定时任务处理：总结昨天
                processUserSummary(user, LocalDate.now().minusDays(1));
            } catch (Exception e) {
                log.error("用户 {} 总结生成失败", user.getOpenId(), e);
            }
        }
        
        // 2. 如果今天是周一，触发周报 (统计过去7天，即上周一至上周日)
        if (LocalDate.now().getDayOfWeek() == DayOfWeek.MONDAY) {
            log.info("今天是周一，开始触发周报任务...");
            try {
                weeklySummaryService.generateWeeklySummaries();
            } catch (Exception e) {
                log.error("周报任务触发失败", e);
            }
        }
    }
    
    /**
     * 手动触发指定用户的总结 (用于测试)
     * @return 执行结果消息
     */
    public String triggerSummaryForUser(UserConfig user) {

        try {
            // 手动触发：默认总结今天 (方便立即看效果)
            // 或者你可以改为总结昨天，看需求。这里暂定为今天。
            return processUserSummary(user, LocalDate.now());
        } catch (Exception e) {
            log.error("手动触发总结失败", e);
            return "生成失败: " + e.getMessage();
        }
    }

    private String processUserSummary(UserConfig userConfig, LocalDate targetDate) {
        String apiKey;
        if (userConfig.getIsGuest()) {
            apiKey = notionProperties.getAdminToken();
        } else {
            apiKey = AesUtil.decrypt(aesKey, userConfig.getEncryptedApiKey());
        }
        String dbId = userConfig.getDatabaseId();

        // 1. 找到目标日期的页面
        String pageId = notionService.findPageByDate(apiKey, dbId, targetDate);
        if (pageId == null) {
            log.info("用户 {} 在 {} 无页面，跳过总结", userConfig.getOpenId(), targetDate);
            return "该日期无页面";
        }

        // 2. 读取页面内容
        JsonNode blocks = notionService.retrieveBlockChildren(apiKey, pageId);
        if (blocks == null) {
            return "读取页面失败";
        }
        
        String rawContent = BlockContentParser.parse(blocks);
        if (rawContent.trim().isEmpty()) {
             log.info("用户 {} 今日页面无有效内容", userConfig.getOpenId());
             return "页面无内容";
        }

        // 3. AI 分析 (返回 JSON)
        DailySummaryResult summaryObj = callAiToAnalyze(userConfig, rawContent);
        if (summaryObj == null) {
            return "AI 分析失败";
        }

        // 4. 写回 Notion (转换为 Markdown 写入 Description)
        boolean success = notionService.updatePageProperty(apiKey, pageId, "Description", summaryObj.toMarkdown());
        
        // 5. 生成并推送日签图片（用户可通过设置关闭）
        com.example.wxnotion.model.PromptConfig promptConfig = userConfig.getPromptConfig();
        boolean cardEnabled = promptConfig == null
                || promptConfig.getDailyCardEnabled() == null
                || promptConfig.getDailyCardEnabled();
        if (cardEnabled) {
            try {
                pushDailyCard(userConfig.getOpenId(), summaryObj);
            } catch (Exception e) {
                log.error("日签图片推送失败", e);
            }
        } else {
            log.info("用户 {} 已关闭每日推图，跳过图片生成", userConfig.getOpenId());
        }

        if (success) {
            log.info("用户 {} 总结已生成并写入 Notion", userConfig.getOpenId());
            return "总结生成成功，日签图片已推送";
        } else {
            return "写入 Notion 失败";
        }
    }
    
    /**
     * 生成并推送日签图片 (基于结构化数据)
     */
    private void pushDailyCard(String openId, DailySummaryResult summary) {
        String yesterdaySummary = summary.yesterdaySummary != null ? summary.yesterdaySummary : "昨日平淡而充实，为今天积蓄力量。";
        String quote = summary.todayQuote != null ? summary.todayQuote : "每一天都是新的开始。";
        String keywords = summary.keywords != null ? summary.keywords : "#每日回响 #InspirationSeconds";
        
        File image = null;
        try {
            // 不再传递本地路径字符串，而是让 ImageGenerator 内部自行加载资源
            image = ImageGenerator.generateDailyCard(yesterdaySummary, quote, keywords);
            weChatService.pushImageToUser(openId, image);
        } catch (Exception e) {
            log.error("图片生成或推送异常", e);
        } finally {
            // 清理临时文件
            if (image != null && image.exists()) {
                boolean deleted = image.delete();
                if (deleted) {
                    log.debug("临时图片已清理: {}", image.getName());
                } else {
                    log.warn("临时图片清理失败: {}", image.getAbsolutePath());
                }
            }
        }
    }
    
    /**
     * 调用 AI 进行分析，集成动态 Prompt 优化机制，返回结构化结果。
     */
    private DailySummaryResult callAiToAnalyze(UserConfig userConfig, String userNotes) {
        // 1. 尝试优化 Prompt (预检查 + 优化)
        try {
            boolean optimized = promptOptimizationService.optimizePromptIfNecessary(userConfig, userNotes);
            if (optimized) {
                log.info("用户 {} 的 Prompt 已根据今日内容动态优化", userConfig.getOpenId());
            }
        } catch (Exception e) {
            log.warn("Prompt 优化流程出现异常，将使用现有配置继续: {}", e.getMessage());
        }

        // 2. 组装最终的 System Prompt
        String systemPrompt = promptManager.assembleSystemPrompt(userConfig);

        // 3. 调用 AI 并反序列化为结构化对象
        try {
            return aiService.chatForObject(systemPrompt, userNotes, DailySummaryResult.class);
        } catch (Exception e) {
            log.error("日报 AI 响应解析失败，用户: {}", userConfig.getOpenId(), e);
            return null;
        }
    }
}
