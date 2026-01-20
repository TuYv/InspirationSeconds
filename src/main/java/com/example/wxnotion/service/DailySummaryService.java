package com.example.wxnotion.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.wxnotion.mapper.UserConfigRepository;
import com.example.wxnotion.model.ConfigStatus;
import com.example.wxnotion.model.UserConfig;
import com.example.wxnotion.util.AesUtil;
import com.example.wxnotion.util.BlockContentParser;
import com.example.wxnotion.util.ContentUtil;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/**
 * 每日 AI 总结服务。
 * 职责：
 * 1. 定时获取 Notion 当日页面内容
 * 2. 调用 AI 进行分析 (TODO: 接入真实 AI)
 * 3. 将总结写回 Notion
 */
import com.example.wxnotion.util.ImageGenerator;
import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailySummaryService {

    private final UserConfigRepository userConfigRepository;
    private final NotionService notionService;
    private final AiService aiService;
    private final WeChatService weChatService;
    private final WeeklySummaryService weeklySummaryService;

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
        List<UserConfig> users = userConfigRepository.selectList(new QueryWrapper<UserConfig>().eq("status", ConfigStatus.ACTIVE));

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
            // 为了不阻塞日报，建议这里也 catch 一下，或者 WeeklyService 内部处理了异常
            try {
                weeklySummaryService.generateWeeklySummaries();
            } catch (Exception e) {
                log.error("周报任务触发失败", e);
            }
        }
    }
    
    /**
     * 手动触发指定用户的总结 (用于测试)
     * @param openId 用户 OpenID
     * @return 执行结果消息
     */
    public String triggerSummaryForUser(String openId) {
        UserConfig user = userConfigRepository.selectOne(new QueryWrapper<UserConfig>().eq("open_id", openId));
        if (user == null || user.getStatus() != ConfigStatus.ACTIVE) {
            user = new UserConfig();
            user.setOpenId(openId);
            user.setDatabaseId("2e904d7490b480bdaca6d08b49a58c94");
            user.setEncryptedApiKey("6B1xuaN4fgAnAD/lYfgTaw==:O/n3t5El8R5QVNnVrAqnxtDASfw7Hf4vJxYZmYC4EJLQe8DFr//5HvHW6h6PbxLnNSzXxoS1dGl1MFdlZQ4xzQ==");
//            return "用户未配置或未激活";
        }
        try {
            // 手动触发：默认总结今天 (方便立即看效果)
            // 或者你可以改为总结昨天，看需求。这里暂定为今天。
            return processUserSummary(user, LocalDate.now());
        } catch (Exception e) {
            log.error("手动触发总结失败", e);
            return "生成失败: " + e.getMessage();
        }
    }

    private String processUserSummary(UserConfig user, LocalDate targetDate) {
        String apiKey = AesUtil.decrypt(aesKey, user.getEncryptedApiKey());
        String dbId = user.getDatabaseId();

        // 1. 找到目标日期的页面
        String pageId = notionService.findPageByDate(apiKey, dbId, targetDate);
        if (pageId == null) {
            log.info("用户 {} 在 {} 无页面，跳过总结", user.getOpenId(), targetDate);
            return "该日期无页面";
        }

        // 2. 读取页面内容
        JsonNode blocks = notionService.retrieveBlockChildren(apiKey, pageId);
        if (blocks == null) {
            return "读取页面失败";
        }
        
        String rawContent = BlockContentParser.parse(blocks);
        if (rawContent.trim().isEmpty()) {
             log.info("用户 {} 今日页面无有效内容", user.getOpenId());
             return "页面无内容";
        }

        // 3. AI 分析
        String summary = callAiToAnalyze(rawContent);

        // 4. 写回 Notion (写入 Description 属性)
        boolean success = notionService.updatePageProperty(apiKey, pageId, "Description", summary);
        
        // 5. 生成并推送日签图片 (如果配置了微信推送)
        try {
            pushDailyCard(user.getOpenId(), summary);
        } catch (Exception e) {
            log.error("日签图片推送失败", e);
        }

        if (success) {
            log.info("用户 {} 总结已生成并写入 Notion", user.getOpenId());
            return "总结生成成功，日签图片已推送";
        } else {
            return "写入 Notion 失败";
        }
    }
    
    /**
     * 生成并推送日签图片
     */
    private void pushDailyCard(String openId, String aiSummary) {
        // 简单的正则提取，尝试从 AI 总结中找到金句和关键词
        // 这里简化处理：直接取“明日建议”作为金句，取“今日概览”的前几个词作为关键词
        // 更好的做法是让 AI 直接输出 JSON 格式
        
        String quote = extractSection(aiSummary, "今日启示");
        if (quote.isEmpty()) quote = "每一天都是新的开始。";
        
        // 提取关键词 (模拟)
        String keywords = "#每日回响 #InspirationSeconds";
        
        try {
            File image = ImageGenerator.generateDailyCard(quote, keywords);
            weChatService.pushImageToUser(openId, image);
        } catch (Exception e) {
            log.error("图片生成异常", e);
        }
    }

    private String extractSection(String text, String sectionName) {
        Pattern p = Pattern.compile("## .*?" + sectionName + "\\s*(.*?)(?=##|$)", Pattern.DOTALL);
        Matcher m = p.matcher(text);
        if (m.find()) {
            return m.group(1).trim();
        }
        return "";
    }
    
    /**
     * 调用 AI 进行分析
     */
    private String callAiToAnalyze(String userNotes) {
        String systemPrompt = """
            你是一个极具洞察力的私人生活助理，你的任务是阅读用户昨天一整天的碎片化笔记，区分其中用户自己的记录或者摘抄的文案, 生成一份“每日回响”日报。
            
            请严格按照以下 Markdown 格式输出（不要包含 Markdown 代码块标记）：
            📝 昨日回响
            (用一段话精炼概括昨天发生的主要内容和亮点，字数 100 字以内)
            🎭 情绪气象台
            (分析昨天笔记中流露的情绪起伏，给出一个天气隐喻，例如：🌤️ 多云转晴，并简述原因)
            💡 潜意识连接
            (尝试找出昨天看似无关的记录之间的潜在联系，或者用户反复提及的主题)
            🔮 今日启示
            (基于昨天的状态和经历，为今天给出一个具体的行动建议或一句鼓励的话，开启新的一天)
            除了昨日回响 其他项在没有明确逻辑的印证时允许为空,即可以没有但是不能不准。
            """;
            
        return aiService.chat(systemPrompt, userNotes);
    }
}
