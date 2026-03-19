## 1. 基础设施

- [x] 1.1 `pom.xml` 添加 `spring-boot-starter-quartz` 依赖
- [x] 1.2 `application.yml` 添加 Quartz RAMJobStore 配置（`spring.quartz.job-store-type: memory`）
- [x] 1.3 `application.yml` 新增 `wx.task-template-id` 配置项（对应环境变量 `WX_TASK_TEMPLATE_ID`）
- [x] 1.4 `WechatService` 新增 `pushTemplateMessage(openId, taskName, remindContent, progress)` 方法（模板消息 API，未配置 ID 时降级日志）

## 2. TaskNotionService 扩展

- [x] 2.1 `TaskNotionService.updateTaskStatus(userConfig, pageId, status)` — 更新 Status select 属性
- [x] 2.2 `TaskNotionService.updateTaskProgress(userConfig, pageId, progress)` — 更新 Progress rich_text 属性
- [x] 2.3 `TaskNotionService.writeCronExpr(userConfig, pageId, cron)` — 写入 CronExpr rich_text 属性
- [x] 2.4 `TaskNotionService.getCronExpr(userConfig, pageId)` — 读取 CronExpr 属性（供启动重建用）
- [x] 2.5 Tasks Database 创建时新增 `CronExpr`（rich_text）字段（更新 `createTasksDatabase` payload）

## 3. 进度更新与任务终结

- [x] 3.1 创建 `TaskLifecycleService.updateProgress(userConfig, taskPageId, message)` — 追加进度消息到对话历史 + 更新 Progress 属性，返回回复文本
- [x] 3.2 创建 `TaskLifecycleService.detectTerminationIntent(message, activeTaskSummary)` — AI 识别终结意图，返回 `{isTermination, status, taskName, taskPageId}`
- [x] 3.3 `TaskLifecycleService.executeTermination(userConfig, taskPageId, taskName, status)` — 更新 Notion Status，追加终结记录，取消 Quartz Job，返回回复文本
- [x] 3.4 终结回复文本：`completed` → "✅ 任务「X」已完成，恭喜！"，`abandoned` → "好的，任务「X」已放弃。"，`deleted` → "已删除任务「X」。"

## 4. 动态提醒（Quartz）

- [x] 4.1 创建 `TaskReminderJob` — 实现 `quartz.Job`，执行时调用 `WechatService.pushTemplateMessage`
- [x] 4.2 创建 `TaskReminderService.generateCronForTask(taskName, taskType, cycle)` — 调用 AI 生成 cron，用 `CronExpression.isValidExpression()` 校验，非法则返回默认 `0 0 20 * * ?`
- [x] 4.3 `TaskReminderService.scheduleReminder(userConfig, taskPageId, taskName, cron)` — 注册 Quartz Job（先 deleteJob 再创建，避免重复）
- [x] 4.4 `TaskReminderService.cancelReminder(taskPageId)` — 取消 Quartz Job（终结任务时调用）
- [x] 4.5 在 `HandlerWxPortalService.finalizeDraft()` 之后调用 `generateCronForTask` + `scheduleReminder` + `writeCronExpr`
- [x] 4.6 创建 `TaskJobRebuildRunner` — 实现 `ApplicationRunner`，异步扫描所有 active 任务，读取 CronExpr 重建 Quartz Job

## 5. 巡检提醒

- [x] 5.1 创建 `TaskPatrolService.patrol()` — `@Scheduled(cron = "0 0 10,22 * * ?", zone = "Asia/Shanghai")`
- [x] 5.2 巡检 AI prompt：输入各用户任务列表（名称/类型/周期/最后更新时间），输出需提醒的任务列表和提醒内容
- [x] 5.3 巡检推送：对 AI 返回的需提醒任务调用 `pushTemplateMessage`，单个失败 catch 日志继续

## 6. 消息路由修改

- [x] 6.1 AI 检测结果 `relatedTaskId != null` 时调用 `TaskLifecycleService.updateProgress()`
- [x] 6.2 `is_task=false && relatedTaskId=null` 时追加终结意图检测，识别到直接调用 `executeTermination()`
- [x] 6.3 终结意图未识别到时走原有普通笔记分支

## 7. 测试

- [x] 7.1 单测 `TaskLifecycleService`：进度更新追加历史、终结意图识别、三种终结状态回复文本
- [x] 7.2 单测 `TaskReminderService`：AI cron 生成校验、非法 cron 兜底、Job 注册与取消
- [x] 7.3 单测 `TaskPatrolService`：巡检 AI 判断、单用户失败不中断、模板 ID 未配置降级
- [x] 7.4 集成测试消息路由：relatedTaskId 非空走进度更新、终结意图直接执行、其余走笔记
