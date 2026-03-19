## Requirements

### Requirement: AI 识别终结意图后直接执行状态变更
当用户消息包含任务终结意图时，系统 SHALL 直接更新 Notion 任务状态并回复结果，无需确认步骤。AI 识别应保守（置信度高才触发），误判概率低，无需额外交互摩擦。

#### Scenario: 识别到完成意图直接执行
- **WHEN** 用户发送 "《原则》读完了"，AI 识别为 completed 且匹配到任务名
- **THEN** 系统立即更新 Notion Status → completed，追加终结记录，回复 "✅ 任务「读完《原则》」已完成，恭喜！"

#### Scenario: 识别到放弃意图直接执行
- **WHEN** 用户发送 "算了，跑步这个任务不做了"，AI 识别为 abandoned
- **THEN** 系统立即更新 Status → abandoned，追加 "🏳️ 任务已放弃" 到对话历史，回复 "好的，任务「每天跑步」已放弃。"

#### Scenario: 识别到删除意图直接软删除
- **WHEN** 用户发送 "删掉背单词那个任务"，AI 识别为 deleted
- **THEN** 系统立即更新 Status → deleted（数据保留），取消对应 Quartz Job，回复 "已删除任务「每天背单词」。"

---

### Requirement: 三种终结状态语义区分
系统 SHALL 根据 AI 识别的意图，区分 completed（达成目标）、abandoned（主动放弃，保留历史）、deleted（软删除，条目保留不展示）。

#### Scenario: 终结后取消 Quartz 提醒
- **WHEN** 任务终结（任意状态）
- **THEN** 系统调用 `TaskReminderService.cancelReminder(taskPageId)`，停止后续提醒推送
