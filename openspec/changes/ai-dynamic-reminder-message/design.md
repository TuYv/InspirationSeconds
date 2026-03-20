## Context

当前两条推送路径均依赖模板消息：

- **Quartz Job**（`TaskReminderJob`）：触发时构造固定格式文本 `"📌 任务提醒：{name}\n该更新进度啦！\n当前进度：{progress}"` 并调用 `pushTemplateMessage`
- **巡检**（`TaskPatrolService`）：AI 已通过 `TASK_PATROL_PROMPT` 生成 `remind_message` 字段，但推送时仍将其包裹在固定前缀格式中再调用 `pushTemplateMessage`

模板消息被微信平台拒绝后需换回客服消息。切换的同时，去掉硬编码格式包装，让 AI 完全负责消息文案。

## Goals / Non-Goals

**Goals:**
- 两条路径均改用 `pushMessageToUser`（客服消息）
- 消息文案完全由 AI 生成，不再有硬编码前缀或固定句式
- 删除 `pushTemplateMessage` 方法及 `WX_TASK_TEMPLATE_ID` 配置

**Non-Goals:**
- 不处理客服消息 48 小时限制的兜底（发送失败记日志即可，现有逻辑已如此）
- 不改变巡检频率、Quartz Job 调度逻辑

## Decisions

### 1. TaskPatrolService：直接使用 AI 输出的 `remind_message`，不包装格式

`TASK_PATROL_PROMPT` 已要求 AI 生成自然提醒语。只需把推送调用从：
```java
"📌 任务提醒：" + taskName + "\n" + remindMsg + progress
```
改为直接传 `item.remindMessage`，并更新 prompt 让 AI 知道它在生成完整的客服消息文本（可以包含任务名和进度上下文）。

替代方案：保留格式包装只换接口 → 不采用，错过了去掉固化格式的机会。

### 2. TaskReminderJob：新增 AI 调用生成提醒文案

Job 触发时，向 AI 发送任务名 + 当前进度，使用新 prompt `TASK_REMINDER_MESSAGE_PROMPT` 生成完整提醒文本，再调用 `pushMessageToUser`。

`AiService.chat()` 已支持 user-level token 调用，但 Job 的 `JobDataMap` 中没有 `UserConfig`。方案：把 `openId` 存入 `JobDataMap`，Job 执行时通过 `UserConfigRepository` 查出 `UserConfig`，再调用 `aiService.chat(userConfig, prompt, userMsg)`。

替代方案：用管理员 token 调用 AI → 可行但不一致；硬编码文案 → 不采用。

### 3. 更新 `TASK_PATROL_PROMPT` 的输出说明

将 prompt 中的说明从"生成一句提醒语（不超过30字）"调整为"生成完整的客服消息文本，可包含任务名、进度上下文和询问语，语气自然友好"，去掉字数限制以支持更丰富的表达。

### 4. 删除 `pushTemplateMessage` 和 `WX_TASK_TEMPLATE_ID`

两处调用全部迁移完成后，直接删除该方法及配置字段，不留降级路径。

## Risks / Trade-offs

- **AI 延迟**：`TaskReminderJob` 新增一次 AI 调用，触发延迟增加约 1-3 秒 → 提醒场景对实时性要求不高，可接受
- **AI 返回空或异常**：需要 fallback 文案（如 `"[任务名] 该更新进度了~"`），避免推送空消息
- **客服消息 48h 限制**：非活跃用户会收到微信报错 45015 → 记录日志跳过，现有错误处理已覆盖

## Migration Plan

1. 删除 `WechatService.pushTemplateMessage()` 和 `@Value("${wx.taskTemplateId:}")`
2. 在 `AiPrompts` 中新增 `TASK_REMINDER_MESSAGE_PROMPT`；更新 `TASK_PATROL_PROMPT` 的输出说明
3. 更新 `TaskReminderJob`：注入 `UserConfigRepository` 和 `AiService`，调用 AI 生成文案后推送
4. 更新 `TaskPatrolService`：直接使用 `item.remindMessage` 推送，移除格式包装
5. 从 `.env` / `application.yml` 移除 `WX_TASK_TEMPLATE_ID` 相关配置

回滚：git revert 本次提交，重新配置模板 ID 环境变量即可（模板消息代码会恢复）。
