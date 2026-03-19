package com.example.wxnotion.service;

import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Quartz Job：到时推送任务提醒模板消息。
 * JobDataMap 键：openId, taskName, progress
 */
@Slf4j
public class TaskReminderJob implements Job {

    @Autowired
    private WechatService wechatService;

    @Override
    public void execute(JobExecutionContext context) {
        JobDataMap data = context.getMergedJobDataMap();
        String openId = data.getString("openId");
        String taskName = data.getString("taskName");
        String progress = data.getString("progress");

        log.info("Quartz 提醒触发，用户: {}, 任务: {}", openId, taskName);
        wechatService.pushTemplateMessage(openId, taskName, "该更新进度啦！", progress);
    }
}
