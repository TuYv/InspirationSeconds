## Why

用户通过微信发送的消息中，除了随手记录的笔记，还包含大量隐式任务（"我要每天跑步"、"三月底前读完《原则》"）。这些任务目前被当作普通笔记写入 Notion，既无结构化追踪，也无后续提醒，导致任务容易被遗忘、无法复盘。

## What Changes

- 新增消息拦截层：文字消息在写入 Notion 前，先经过 AI 判断是否为任务
- 新增任务追问流程：AI 提取任务字段，字段不足时通过多轮对话向用户补全
- 新增 Notion Tasks Database：独立于现有 Notes Database，每个用户按需创建
- 新增任务对话历史：每个任务的 Notion 页面内以 append-only blocks 记录完整对话
- 新增任务状态管理：active / completed / abandoned / deleted（软删除）
- 新增 `task_draft` 数据库表：存储追问中的草稿及对话历史
- 扩展 `user_config` 表：新增 `tasks_database_id` 字段

**本次不实现（已记录待后续）**：
- 动态提醒（Quartz + 模板消息 + AI 生成 cron）
- 巡检提醒（每天 10:00/22:00 扫描长期未更新任务，AI 判断是否提醒）
- 进度更新（消息关联现有任务，追加对话历史）
- 任务终结（用户触发状态变更）

## Capabilities

### New Capabilities

- `task-detection`: AI 检测消息是否为任务，分类（周期/一次性），提取结构化字段
- `task-clarification`: 多轮追问状态机，支持用户同时有多个待确认任务草稿
- `task-notion-storage`: 在 Notion 创建独立 Tasks Database，写入任务条目及对话历史

### Modified Capabilities

- `message-sync`: 现有消息同步流程需在 SyncService 调用前插入任务检测分支

## Impact

- **新增代码**：`TaskDetectionService`、`TaskDraftService`、`TaskNotionService`
- **修改代码**：`HandlerWxPortalService`（插入任务检测入口）、`SyncService`（调用前判断）
- **数据库变更**：新增 `task_draft` 表，`user_config` 新增 `tasks_database_id` 列
- **外部依赖**：新增 Notion API 调用（创建 Database、创建 Page、追加 Blocks）
- **AI 调用**：每条消息增加一次 AI 检测调用（接受延迟，处理链为异步）
