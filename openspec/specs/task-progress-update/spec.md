## Requirements

### Requirement: 消息关联现有任务时自动追加进度
当 AI 检测到消息的 `related_task_id` 非空且指向一个 active 任务时，系统 SHALL 将该消息作为进度更新处理：追加到对应 Notion 任务页面的对话历史，并更新 Progress 属性。消息不再写入 Notes Database。

#### Scenario: 进度消息关联到 active 任务
- **WHEN** 用户发送 "今天跑了5公里"，AI 检测到 related_task_id 指向 "每天跑步" 任务
- **THEN** 消息追加为任务页对话历史，Progress 属性更新为 "今天跑了5公里"，不调用 SyncService.sync()

#### Scenario: 进度消息回复确认
- **WHEN** 进度更新成功写入 Notion
- **THEN** 系统回复用户 "✅ 进度已记录：[任务名]"

---

### Requirement: 进度更新同时追加系统回复到对话历史
系统 SHALL 在追加用户进度消息的同时，将系统回复也写入对话历史，保持对话历史的完整性。

#### Scenario: 对话历史包含系统回复
- **WHEN** 进度更新处理完成
- **THEN** 任务页对话历史中同时包含用户消息（🧑）和系统回复（🤖），带时间戳
