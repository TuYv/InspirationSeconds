## MODIFIED Requirements

### Requirement: 文字消息处理路由（草稿 → 任务 → 进度更新 → 终结 → 笔记）
`HandlerWxPortalService` 的文字消息路由 SHALL 按以下优先级处理：草稿匹配 → 新任务检测 → 进度更新（relatedTaskId 非空）→ 终结意图检测 → 普通笔记。终结识别到即直接执行，无确认中间状态。

所有有 UserConfig 上下文的 AI 调用点 SHALL 改用 `AiService.chat(UserConfig, prompt, msg)` 重载，以支持用户自定义模型配置。

#### Scenario: 有 PENDING 草稿时优先尝试匹配
- **WHEN** 用户发送文字消息且存在 PENDING task_draft
- **THEN** 系统先调用 AI 判断消息是否为草稿回复；若匹配则更新草稿，不调用 SyncService；若不匹配则继续正常检测流程

#### Scenario: related_task_id 非空时走进度更新分支
- **WHEN** AI 检测结果 related_task_id 非空，指向一个 active 任务
- **THEN** 调用 TaskLifecycleService 追加进度，不走新任务分支，不调用 SyncService

#### Scenario: 终结意图识别在进度更新之后、新任务之前
- **WHEN** AI 检测 is_task=false 且 related_task_id=null，但消息含终结意图
- **THEN** 直接执行终结操作，不调用 SyncService，回复终结确认语

#### Scenario: 所有任务分支均不匹配时走笔记流程
- **WHEN** 无草稿匹配、is_task=false、related_task_id=null、无终结意图
- **THEN** 调用 SyncService.sync()，行为与任务功能上线前相同

#### Scenario: 用户自定义模型时 AI 调用走用户配置
- **WHEN** 用户已配置 ai_api_key，触发任何 AI 调用（任务检测、草稿追问、巡检等）
- **THEN** 调用使用用户自己的 base_url / api_key / model，不走系统全局配置
