package com.example.wxnotion.service;

import com.example.wxnotion.mapper.UserConfigRepository;
import com.example.wxnotion.model.UserConfig;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Quartz Job：到时调用 AI 生成友好提醒文案，通过客服消息推送给用户。
 * JobDataMap 键：openId, taskName, progress
 */
@Slf4j
public class TaskReminderJob implements Job {

    @Autowired
    private WechatService wechatService;

    @Autowired
    private AiService aiService;

    @Autowired
    private UserConfigRepository userConfigRepository;

    @Override
    public void execute(JobExecutionContext context) {
        JobDataMap data = context.getMergedJobDataMap();
        String openId = data.getString("openId");
        String taskName = data.getString("taskName");
        String progress = data.getString("progress");

        log.info("Quartz 提醒触发，用户: {}, 任务: {}", openId, taskName);

        String content = generateReminderMessage(openId, taskName, progress);
        wechatService.pushMessageToUser(openId, content);
    }

    private String generateReminderMessage(String openId, String taskName, String progress) {
        try {
            UserConfig userConfig = userConfigRepository.selectByOpenId(openId);
            if (userConfig == null) {
                return fallback(taskName);
            }
            String userMsg = "任务名：" + taskName
                    + "\n当前进度：" + (progress != null && !progress.isBlank() ? progress : "暂无记录");
            String result = aiService.chat(userConfig, AiPrompts.TASK_REMINDER_MESSAGE_PROMPT, userMsg);
            if (result == null || result.isBlank()) {
                return fallback(taskName);
            }
            return result;
        } catch (Exception e) {
            log.warn("AI 生成提醒文案失败，用户: {}, 任务: {}: {}", openId, taskName, e.getMessage());
            return fallback(taskName);
        }
    }

    private static String fallback(String taskName) {
        return "[" + taskName + "] 该更新进度了~";
    }
}
