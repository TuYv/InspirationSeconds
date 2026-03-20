## Why

微信模板消息"每日进度触发通知"审核被拒，理由是模板消息不支持非用户主动触发的场景。任务提醒（Quartz Job 定时触发、每日两次巡检）均属于系统主动推送，必须改用客服消息。切换为客服消息后，消息内容不再受模板字段约束，可以让 AI 生成更自然、个性化的提醒语言，替代原来硬编码的固定文案。

## What Changes

- 移除 `TaskReminderJob` 和 `TaskPatrolService` 中对 `pushTemplateMessage` 的调用，改用 `pushMessageToUser`
- 移除 `WX_TASK_TEMPLATE_ID` 环境变量依赖及 `WechatService.pushTemplateMessage()` 方法
- `TaskReminderJob` 触发时，调用 AI 根据任务名称和当前进度生成一段友好的询问文案，再通过客服消息推送
- `TaskPatrolService` 巡检时，AI 不仅判断"是否需要提醒"，同时输出本次提醒应使用的个性化消息文本
- 客服消息受 48 小时活跃窗口限制，推送失败时记录日志并跳过，不影响其他用户

## Capabilities

### New Capabilities

（无新 capability，均为已有功能的行为变更）

### Modified Capabilities

- `task-dynamic-reminder`：Quartz Job 推送方式从模板消息改为客服消息，消息文案由 AI 动态生成，移除模板 ID 降级逻辑
- `task-patrol-reminder`：巡检推送方式从模板消息改为客服消息，AI 输出结构增加 `message` 字段用于个性化提醒文本

## Impact

- `WechatService`：删除 `pushTemplateMessage()` 方法及 `taskTemplateId` 字段
- `TaskReminderJob`：新增 AI 调用生成消息文案
- `TaskPatrolService` / `TaskPatrolItem`：`TaskPatrolItem` 增加 `message` 字段，巡检使用该字段推送
- `AiService` / `AiPrompts`：新增或更新任务提醒文案生成的 prompt
- 环境变量：`WX_TASK_TEMPLATE_ID` 可从配置中移除
