## Why

第一阶段完成了任务的检测、追问和写入 Notion，但任务创建后缺乏后续的生命周期管理：用户无法更新进度、无法终结任务、系统也不会主动提醒。这导致任务追踪形同虚设——记录了但没有跟进。

## What Changes

- **进度更新**：AI 检测到消息关联现有任务时，将消息追加为任务对话历史，并更新 Notion task page 的 Progress 属性
- **任务终结**：AI 识别完成/放弃/删除意图，更新 Notion task page Status，追加终结记录到对话历史
- **动态提醒**：任务创建后 AI 输出 cron 表达式，注册 Quartz Job；到时推送微信模板消息询问进度，收到回复后更新进度并重新调度
- **巡检提醒**：每天 10:00/22:00 扫描 active 任务，AI 动态判断哪些任务需要提醒并推送模板消息
- **新增依赖**：引入 `spring-boot-starter-quartz`（RAMJobStore，重启后从 Notion 重建）
- **新增 WechatService 方法**：`pushTemplateMessage`（模板消息，绕过 48h 限制）

## Capabilities

### New Capabilities

- `task-progress-update`: 用户消息关联现有任务时，追加进度到 Notion 任务页
- `task-termination`: 用户触发任务终结（completed/abandoned/deleted），更新状态
- `task-dynamic-reminder`: Quartz 动态 Job，AI 生成 cron，到时推送模板消息
- `task-patrol-reminder`: 每天两次巡检，AI 判断是否需要提醒，推送模板消息

### Modified Capabilities

- `message-sync`: 消息路由新增进度更新和任务终结分支（related_task_id 非空或终结意图）
- `task-detection`: AI 检测结果中 related_task_id 字段开始被使用（此前仅记录日志）

## Impact

- **新增代码**：`TaskReminderService`（Quartz Job 注册/重建）、`TaskPatrolService`（巡检）、`TaskLifecycleService`（进度更新+终结）
- **修改代码**：`HandlerWxPortalService`（新增进度更新/终结路由）、`WechatService`（新增模板消息方法）、`TaskNotionService`（新增 updateTaskStatus/updateProgress 方法）
- **新增依赖**：`spring-boot-starter-quartz`
- **外部依赖**：微信公众号模板消息（需预先申请模板 ID，配置到环境变量）
- **启动行为变更**：应用启动时从 Notion 加载所有用户 active 任务，重建 Quartz Job
