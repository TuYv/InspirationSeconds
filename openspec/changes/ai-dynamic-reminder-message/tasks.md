## 1. 清理模板消息代码

- [x] 1.1 删除 `WechatService` 中的 `pushTemplateMessage()` 方法及 `@Value("${wx.taskTemplateId:}")` 字段
- [x] 1.2 删除 `WechatService` 中未使用的 `WxMpTemplateData`、`WxMpTemplateMessage` import

## 2. 更新 AI Prompt

- [x] 2.1 在 `AiPrompts` 中新增 `TASK_REMINDER_MESSAGE_PROMPT`：输入任务名+进度，输出完整客服消息文案（纯文本，含友好询问语气）
- [x] 2.2 更新 `TASK_PATROL_PROMPT`：将输出说明从"不超过30字的提醒语"改为"完整客服消息文本，含任务名、进度上下文和询问语，语气自然友好"，去掉字数限制

## 3. 更新 TaskReminderJob

- [x] 3.1 在 `TaskReminderJob` 中注入 `UserConfigRepository` 和 `AiService`
- [x] 3.2 将 `openId` 写入 `JobDataMap` 的逻辑确认无需修改（已有），在 Job 执行时通过 `openId` 查询 `UserConfig`
- [x] 3.3 调用 `aiService.chat(userConfig, TASK_REMINDER_MESSAGE_PROMPT, "任务名：{name}\n当前进度：{progress}")` 生成文案
- [x] 3.4 AI 调用失败时使用 fallback 文案 `"[taskName] 该更新进度了~"`
- [x] 3.5 将生成文案通过 `wechatService.pushMessageToUser(openId, content)` 推送

## 4. 更新 TaskPatrolService

- [x] 4.1 移除推送时的硬编码格式包装，直接使用 `item.remindMessage` 作为客服消息内容
- [x] 4.2 确认 `item.remindMessage` 为 null 时的 fallback 处理

## 5. 清理配置

- [x] 5.1 从 `application.yml` 中移除 `wx.taskTemplateId` 配置项（如存在）
- [x] 5.2 从 `.env` 或部署文档中移除 `WX_TASK_TEMPLATE_ID` 说明
