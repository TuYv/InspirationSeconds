## MODIFIED Requirements

### Requirement: 文字消息处理增加进度更新和任务终结分支
`HandlerWxPortalService` 的文字消息路由 SHALL 在现有「草稿匹配 → 新任务检测 → 普通笔记」基础上，新增进度更新分支和终结意图检测分支。终结识别到即直接执行，无确认中间状态。

#### Scenario: related_task_id 非空时走进度更新分支
- **WHEN** AI 检测结果 related_task_id 非空，指向一个 active 任务
- **THEN** 调用 TaskLifecycleService 追加进度，不走新任务分支，不调用 SyncService

#### Scenario: 终结意图识别在进度更新之后、新任务之前
- **WHEN** AI 检测 is_task=false 且 related_task_id=null，但消息含终结意图
- **THEN** 直接执行终结操作，不调用 SyncService，回复终结确认语

#### Scenario: 所有任务分支均不匹配时走笔记流程
- **WHEN** 无草稿匹配、is_task=false、related_task_id=null、无终结意图
- **THEN** 调用 SyncService.sync()，行为与第一阶段前相同
