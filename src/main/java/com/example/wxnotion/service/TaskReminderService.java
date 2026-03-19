package com.example.wxnotion.service;

import com.example.wxnotion.model.CronResult;
import com.example.wxnotion.model.UserConfig;
import org.apache.commons.lang3.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.stereotype.Service;

/**
 * Quartz 动态提醒调度服务。
 * - AI 生成 cron 表达式
 * - 注册 / 取消 Quartz Job
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskReminderService {

    private final Scheduler scheduler;
    private final AiService aiService;

    private static final String DEFAULT_CRON = "0 0 20 * * ?"; // 每天 20:00
    private static final String GROUP = "task-reminders";

    /**
     * AI 生成 cron，校验合法性，非法则返回默认值。
     */
    public String generateCronForTask(UserConfig user, String taskName, String taskType, String cycle) {
        String userContent = "任务名：" + taskName
                + "\n类型：" + taskType
                + "\n周期/描述：" + (cycle != null ? cycle : "无");

        try {
            CronResult result = aiService.chatForObject(user, AiPrompts.CRON_GENERATION_PROMPT, userContent, CronResult.class);
            if (result.cron != null && CronExpression.isValidExpression(result.cron)) {
                return result.cron;
            }
            log.warn("AI 生成的 cron 非法: '{}', 使用默认值, 任务: {}", result.cron, taskName);
        } catch (Exception e) {
            log.warn("cron 生成解析失败, 任务: {}: {}", taskName, e.getMessage());
        }
        return DEFAULT_CRON;
    }

    /**
     * 注册 Quartz Job（先删再建，避免重复）。
     */
    public void scheduleReminder(UserConfig userConfig, String taskPageId, String taskName, String cron) {
        try {
            JobKey key = JobKey.jobKey(taskPageId, GROUP);
            scheduler.deleteJob(key);

            JobDetail job = JobBuilder.newJob(TaskReminderJob.class)
                    .withIdentity(key)
                    .usingJobData("openId", userConfig.getOpenId())
                    .usingJobData("taskName", taskName)
                    .usingJobData("progress", "")
                    .storeDurably()
                    .build();

            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(taskPageId, GROUP)
                    .withSchedule(CronScheduleBuilder.cronSchedule(cron)
                            .inTimeZone(java.util.TimeZone.getTimeZone("Asia/Shanghai")))
                    .build();

            scheduler.scheduleJob(job, trigger);
            log.info("Quartz Job 已注册，任务: {}, cron: {}", taskName, cron);
        } catch (SchedulerException e) {
            log.error("注册 Quartz Job 失败，任务: {}: {}", taskName, e.getMessage());
        }
    }

    /**
     * 取消 Quartz Job（任务终结时调用）。
     */
    public void cancelReminder(String taskPageId) {
        try {
            scheduler.deleteJob(JobKey.jobKey(taskPageId, GROUP));
            log.info("Quartz Job 已取消，pageId: {}", taskPageId);
        } catch (SchedulerException e) {
            log.warn("取消 Quartz Job 失败，pageId: {}: {}", taskPageId, e.getMessage());
        }
    }
}
