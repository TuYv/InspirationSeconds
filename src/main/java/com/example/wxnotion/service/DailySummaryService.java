package com.example.wxnotion.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.wxnotion.mapper.UserConfigRepository;
import com.example.wxnotion.model.ConfigStatus;
import com.example.wxnotion.model.UserConfig;
import com.example.wxnotion.util.AesUtil;
import com.example.wxnotion.util.BlockContentParser;
import com.example.wxnotion.util.ContentUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import com.example.wxnotion.util.ImageGenerator;
import java.io.File;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailySummaryService {

    private final UserConfigRepository userConfigRepository;
    private final NotionService notionService;
    private final AiService aiService;
    private final WeChatService weChatService;
    private final WeeklySummaryService weeklySummaryService;
    private final ObjectMapper mapper = new ObjectMapper();

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

    // 内部类用于承载 AI 解析结果
    private static class AiDailySummary {
        public String yesterday_summary;
        public String emotion_weather;
        public String subconscious_link;
        public String today_quote;
        public String keywords;
        
        // 转换为 Markdown 格式用于写入 Notion
        public String toMarkdown() {
            StringBuilder sb = new StringBuilder();
            if (yesterday_summary != null) sb.append("📝 昨日回响\n").append(yesterday_summary).append("\n\n");
            if (emotion_weather != null) sb.append("🎭 情绪气象台\n").append(emotion_weather).append("\n\n");
            if (subconscious_link != null) sb.append("💡 潜意识连接\n").append(subconscious_link).append("\n\n");
            if (today_quote != null) sb.append("🔮 今日启示\n").append(today_quote).append("\n\n");
            if (keywords != null) sb.append("🏷️ 关键词\n").append(keywords);
            return sb.toString();
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

        // 3. AI 分析 (返回 JSON)
        String jsonResult = callAiToAnalyze(rawContent);
        AiDailySummary summaryObj = parseAiResponse(jsonResult);
        
        if (summaryObj == null) {
            return "AI 分析失败";
        }

        // 4. 写回 Notion (转换为 Markdown 写入 Description)
        boolean success = notionService.updatePageProperty(apiKey, pageId, "Description", summaryObj.toMarkdown());
        
        // 5. 生成并推送日签图片 (使用解析后的对象)
        try {
            pushDailyCard(user.getOpenId(), summaryObj);
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
     * 解析 AI 返回的 JSON
     */
    private AiDailySummary parseAiResponse(String json) {
        try {
            // 清理可能的 Markdown 代码块标记 (```json ... ```)
            String cleanJson = json.replaceAll("(?s)^```json\\s*", "").replaceAll("(?s)\\s*```$", "").trim();
            // 有时候 AI 可能会在 ```json 后换行，或者不加 json，只加 ```
            if (cleanJson.startsWith("```")) {
                 cleanJson = cleanJson.replaceAll("(?s)^```\\w*\\s*", "").replaceAll("(?s)\\s*```$", "").trim();
            }
            return mapper.readValue(cleanJson, AiDailySummary.class);
        } catch (JsonProcessingException e) {
            log.error("AI 响应 JSON 解析失败: raw={}", json, e);
            return null;
        }
    }

    /**
     * 生成并推送日签图片 (基于结构化数据)
     */
    private void pushDailyCard(String openId, AiDailySummary summary) {
        String yesterdaySummary = summary.yesterday_summary != null ? summary.yesterday_summary : "昨日平淡而充实，为今天积蓄力量。";
        String quote = summary.today_quote != null ? summary.today_quote : "每一天都是新的开始。";
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
     * 调用 AI 进行分析 (强制 JSON)
     */
    private String callAiToAnalyze(String userNotes) {
        String systemPrompt = """
            你是一个极具洞察力的私人生活助理，你的任务是阅读用户昨天一整天的碎片化笔记，生成一份“每日回响”日报。
            
            请直接返回标准 JSON 格式数据，不要包含 Markdown 标记，字段定义如下：
            {
              "yesterday_summary": "用一段话精炼概括昨天发生的主要内容和亮点，字数 100 字以内",
              "emotion_weather": "分析情绪起伏，给出一个天气隐喻(如🌤️ 多云转晴)，简述原因。无明显情绪可为空字符串",
              "subconscious_link": "找出潜在联系或重复主题。无内容可为空字符串",
              "today_quote": "基于昨天经历，从经典名著/诗句/动漫/影视剧/歌曲中找出一句符合场景的一句话并给出出处，你需要做好断句给好换行",
              "keywords": "提取2-5个最能代表昨天的关键词，用空格分隔，如 #阅读 #冥想 #效率"
            }
            
            只返回 JSON，不要返回其他废话。
            """;
            
        return aiService.chat(systemPrompt, userNotes);
    }
}
