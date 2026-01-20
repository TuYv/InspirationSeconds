package com.example.wxnotion.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.wxnotion.mapper.UserConfigRepository;
import com.example.wxnotion.model.ConfigStatus;
import com.example.wxnotion.model.UserConfig;
import com.example.wxnotion.util.AesUtil;
import com.example.wxnotion.util.ContentUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;

/**
 * 每周 AI 总结服务。
 * 职责：
 * 1. 每周一收集过去 7 天的 Daily Summary (Description 属性)
 * 2. 调用 AI 生成 Weekly Summary
 * 3. 创建周报页面并推送到 Notion (及微信)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklySummaryService {

    private final UserConfigRepository userConfigRepository;
    private final NotionService notionService;
    private final AiService aiService;
    private final WeChatService weChatService; // 如果需要推微信

    @Value("${security.aesKey}")
    private String aesKey;

    /**
     * 每周一 08:00 由 DailySummaryService 触发
     */
    public void generateWeeklySummaries() {
        log.info("开始执行每周 AI 总结任务...");
        List<UserConfig> users = userConfigRepository.selectList(new QueryWrapper<UserConfig>().eq("status", ConfigStatus.ACTIVE));

        for (UserConfig user : users) {
            try {
                processUserWeeklySummary(user);
            } catch (Exception e) {
                log.error("用户 {} 周报生成失败", user.getOpenId(), e);
            }
        }
    }

    public String processUserWeeklySummary(UserConfig user) {
        String apiKey = AesUtil.decrypt(aesKey, user.getEncryptedApiKey());
        String dbId = user.getDatabaseId();
        
        // 1. 收集过去 7 天的 Summary
        StringBuilder weeklyInput = new StringBuilder();
        LocalDate today = LocalDate.now();
        
        // 过去7天：从上周一到上周日 (如果今天是周一，那就是 minusDays(7) 到 minusDays(1))
        for (int i = 7; i >= 1; i--) {
            LocalDate date = today.minusDays(i);
            String pageId = notionService.findPageByDate(apiKey, dbId, date);
            if (pageId != null) {
                String desc = notionService.getPageProperty(apiKey, pageId, "Description");
                if (desc != null && !desc.isEmpty()) {
                    weeklyInput.append("## ").append(date.toString()).append("\n")
                               .append(desc).append("\n\n");
                }
            }
        }
        
        if (weeklyInput.length() == 0) {
            log.info("用户 {} 过去一周无有效总结，跳过周报", user.getOpenId());
            return "无内容";
        }

        // 2. AI 分析
        String summary = callAiToAnalyzeWeekly(weeklyInput.toString());
        
        // 3. 写入 Notion (创建新页面)
        // 标题：📅 2026年第X周周报 (01.12-01.18)
        LocalDate startOfWeek = today.minusDays(7);
        LocalDate endOfWeek = today.minusDays(1);
        int weekOfYear = startOfWeek.get(WeekFields.of(Locale.getDefault()).weekOfYear());
        
        String title = String.format("📅 %d年第%d周周报 (%s - %s)", 
            startOfWeek.getYear(), weekOfYear, 
            startOfWeek.format(DateTimeFormatter.ofPattern("MM.dd")), 
            endOfWeek.format(DateTimeFormatter.ofPattern("MM.dd")));
            
        ContentUtil.NotionContent content = new ContentUtil.NotionContent();
        content.setTitle(title);
        content.setContent(summary);
        content.setTags(List.of("周报", "Weekly"));
        
        try {
            NotionService.CreateResult result = notionService.createPage(apiKey, dbId, content);
            if (result.ok) {
                // 4. 推送微信通知
                weChatService.sendKefuMessage(user.getOpenId(), "本周周报已生成：\n" + title);
                return "周报生成成功";
            }
        } catch (Exception e) {
            log.error("周报写入失败", e);
        }
        return "失败";
    }

    private String callAiToAnalyzeWeekly(String dailySummaries) {
        String systemPrompt = """
            你是一个极具洞察力的私人生活助理。你的任务是阅读用户过去一周的每日总结（Daily Summary），生成一份深度的“每周回响”周报。
            
            请从以下维度进行分析，严格按照 Markdown 格式输出：
            
            ## 🌟 本周高光
            (识别本周最重要的成就、感悟或幸福时刻)
            
            ## 📈 状态趋势
            (分析本周的情绪、精力或关注点的变化趋势)
            
            ## 🧩 模式识别
            (指出本周反复出现的行为模式、思维陷阱或潜在的长期兴趣点)
            
            ## 🚀 下周聚焦
            (基于本周的情况，为下周提出 1-2 个核心关注点或改进建议)
            """;
            
        return aiService.chat(systemPrompt, dailySummaries);
    }
}
