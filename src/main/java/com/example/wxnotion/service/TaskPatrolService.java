package com.example.wxnotion.service;

import com.example.wxnotion.mapper.UserConfigRepository;
import com.example.wxnotion.model.TaskPatrolItem;
import com.example.wxnotion.model.UserConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 任务巡检服务。每天 10:00 和 22:00 扫描所有 active 任务，
 * AI 动态判断哪些需要提醒，推送客服消息。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskPatrolService {

    private final UserConfigRepository userConfigRepository;
    private final TaskNotionService taskNotionService;
    private final WechatService wechatService;
    private final AiService aiService;

    @Scheduled(cron = "0 0 10,22 * * ?", zone = "Asia/Shanghai")
    public void patrol() {
        log.info("开始任务巡检...");
        List<UserConfig> users = userConfigRepository.selectActiveUsers();

        int reminded = 0;
        for (UserConfig user : users) {
            try {
                reminded += patrolUser(user);
            } catch (Exception e) {
                log.error("巡检用户 {} 失败: {}", user.getOpenId(), e.getMessage());
            }
        }
        log.info("任务巡检完成，共发送提醒 {} 条", reminded);
    }

    private int patrolUser(UserConfig user) {
        String summary = taskNotionService.getActiveTaskSummary(user);
        if ("[]".equals(summary)) return 0;

        try {
            List<TaskPatrolItem> items = aiService.chatForList(
                    user, AiPrompts.TASK_PATROL_PROMPT, "用户任务列表：\n" + summary, TaskPatrolItem.class);
            int count = 0;
            for (TaskPatrolItem item : items) {
                try {
                    String content = item.remindMessage != null && !item.remindMessage.isBlank()
                            ? item.remindMessage
                            : "[" + (item.taskName != null ? item.taskName : "任务") + "] 该更新进度了~";
                    wechatService.pushMessageToUser(user.getOpenId(), content);
                    count++;
                } catch (Exception e) {
                    log.error("巡检推送失败，用户: {}, 任务: {}: {}", user.getOpenId(), item.taskName, e.getMessage());
                }
            }
            return count;
        } catch (Exception e) {
            log.error("巡检 AI 响应解析失败，用户: {}: {}", user.getOpenId(), e.getMessage());
            return 0;
        }
    }
}
