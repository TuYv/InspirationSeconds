## MODIFIED Requirements

### Requirement: 文字消息处理增加任务检测分支
`HandlerWxPortalService` 处理文字消息时，SHALL 优先检查用户是否有 PENDING 草稿或当前消息是否为任务，再决定是否调用 `SyncService.sync()`。非任务消息的笔记同步行为不变。

#### Scenario: 有 PENDING 草稿时优先尝试匹配
- **WHEN** 用户发送文字消息且存在 PENDING task_draft
- **THEN** 系统先调用 AI 判断消息是否为草稿回复；若匹配则更新草稿，不调用 SyncService；若不匹配则继续正常检测流程

#### Scenario: 无草稿且非任务时走正常笔记流程
- **WHEN** 用户发送文字消息，无 PENDING 草稿，AI 判断 is_task: false
- **THEN** 调用 SyncService.sync()，行为与现有逻辑完全一致

#### Scenario: 识别为任务时不重复写入 Notes Database
- **WHEN** AI 判断消息 is_task: true
- **THEN** 消息进入任务处理流程，不调用 SyncService.sync()，用户收到任务创建相关回复而非笔记同步回复
