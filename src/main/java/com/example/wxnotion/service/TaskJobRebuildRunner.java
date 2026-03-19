package com.example.wxnotion.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.wxnotion.mapper.UserConfigRepository;
import com.example.wxnotion.model.ConfigStatus;
import com.example.wxnotion.model.UserConfig;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 应用启动后异步重建所有 active 任务的 Quartz Job。
 * RAMJobStore 重启后 Job 丢失，此处从 Notion 重新加载。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskJobRebuildRunner implements ApplicationRunner {

    private final UserConfigRepository userConfigRepository;
    private final TaskNotionService taskNotionService;
    private final TaskReminderService taskReminderService;

    @Override
    public void run(ApplicationArguments args) {
        rebuildAsync();
    }

    @Async
    public void rebuildAsync() {
        log.info("开始异步重建 Quartz Jobs...");
        List<UserConfig> users = userConfigRepository.selectList(
                new QueryWrapper<UserConfig>().eq("status", ConfigStatus.ACTIVE));

        int rebuilt = 0;
        for (UserConfig user : users) {
            try {
                rebuilt += rebuildForUser(user);
            } catch (Exception e) {
                log.warn("重建用户 {} 的 Job 失败: {}", user.getOpenId(), e.getMessage());
            }
        }
        log.info("Quartz Jobs 重建完成，共重建 {} 个", rebuilt);
    }

    private int rebuildForUser(UserConfig user) {
        String summary = taskNotionService.getActiveTaskSummary(user);
        if ("[]".equals(summary)) return 0;

        int count = 0;
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            JsonNode tasks = mapper.readTree(summary);
            for (JsonNode task : tasks) {
                String pageId = task.path("id").asText(null);
                String name = task.path("name").asText("未命名任务");
                if (pageId == null) continue;

                String cron = taskNotionService.getCronExpr(user, pageId);
                if (cron == null || cron.isBlank()) {
                    log.debug("任务 {} 无 CronExpr，跳过重建", name);
                    continue;
                }
                taskReminderService.scheduleReminder(user, pageId, name, cron);
                count++;
            }
        } catch (Exception e) {
            log.warn("解析用户 {} 任务摘要失败: {}", user.getOpenId(), e.getMessage());
        }
        return count;
    }
}
