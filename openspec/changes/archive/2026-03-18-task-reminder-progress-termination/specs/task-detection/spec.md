## MODIFIED Requirements

### Requirement: related_task_id 字段开始被路由层使用
`TaskDetectionResult.relatedTaskId` 在第一阶段已填充但未使用。本阶段 SHALL 将其作为进度更新分支的路由依据。当该字段非空时，路由层优先走进度更新分支，不再判断是否为新任务。

#### Scenario: related_task_id 非空触发进度更新路由
- **WHEN** AI 检测返回 related_task_id 指向某 active 任务 ID
- **THEN** HandlerWxPortalService 直接路由到进度更新分支，跳过新任务检测

#### Scenario: related_task_id 为 null 时继续原有路由
- **WHEN** AI 检测返回 related_task_id 为 null
- **THEN** 继续原有判断逻辑（is_task → 新任务 / 终结意图 / 普通笔记）
