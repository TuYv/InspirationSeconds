## 1. 数据库变更

- [x] 1.1 在 schema.sql 新增 `task_draft` 表（open_id, draft_json, conversation_log, status, created_at, updated_at）
- [x] 1.2 在 schema.sql 的 `user_config` 表新增 `tasks_database_id VARCHAR(128)` 列
- [x] 1.3 创建 `TaskDraft` 实体类和 `TaskDraftMapper`
- [x] 1.4 在 `UserConfig` 实体类中添加 `tasksDatabaseId` 字段

## 2. Notion Tasks Database 层

- [x] 2.1 在 `NotionApiFacade` 中添加 `createTasksDatabase(token, parentPageId, title)` 方法
- [x] 2.2 在 `NotionApiFacade` 中添加 `appendBlockChildren(token, pageId, blocks)` 方法
- [x] 2.3 创建 `TaskNotionService`：`ensureTasksDatabase(UserConfig)` — 懒初始化 Tasks DB 并回写 ID
- [x] 2.4 `TaskNotionService`：`createTaskPage(userConfig, TaskDraft)` — 写入任务 Properties
- [x] 2.5 `TaskNotionService`：`appendConversationHistory(userConfig, pageId, conversationLog)` — 将对话历史写为 blocks

## 3. AI 检测层

- [x] 3.1 task-detection prompt 内联在 `TaskDetectionService` 常量中
- [x] 3.2 创建 `TaskDetectionService`：`detectTask(message, activeTaskSummary)` — 返回结构化 `TaskDetectionResult`
- [x] 3.3 定义 `TaskDetectionResult` DTO（is_task, task_type, extracted fields, missing_fields, related_task_id）
- [x] 3.4 Prompt 内联管理（不走 PromptManager，独立 service）

## 4. 追问状态机

- [x] 4.1 创建 `TaskDraftService`：`createDraft(openId, originalMessage, detectionResult)` — 创建 PENDING 草稿
- [x] 4.2 `TaskDraftService`：`findPendingDrafts(openId)` — 查询用户所有 PENDING 草稿
- [x] 4.3 `TaskDraftService`：`matchReplyToDraft(openId, message)` — AI 语义匹配回复到对应草稿
- [x] 4.4 `TaskDraftService`：`applyReplyToDraft(draft, field, value, userMsg, assistantReply)` — 填入字段，更新 conversation_log
- [x] 4.5 `TaskDraftService`：`generateClarificationQuestion(draft)` — AI 生成下一个追问话术
- [x] 4.6 `TaskDraftService`：`isDraftComplete(draft)` — 判断草稿是否字段完整可创建任务
- [x] 4.7 `TaskDraftService`：`expireOldDrafts()` — `@Scheduled` 每天凌晨2点清理超过 7 天的 PENDING 草稿

## 5. 消息路由修改

- [x] 5.1 修改 `HandlerWxPortalService.processMessageAsync()`：text 分支路由到 `processTextMessage()`
- [x] 5.2 实现草稿回复分支：匹配草稿 → 填字段 → 若完整则创建 Notion 任务 → 删除草稿 → 回复确认
- [x] 5.3 实现新任务分支：is_task=true → 创建草稿 → 若字段完整直接创建任务 → 否则追问
- [x] 5.4 实现普通笔记分支：is_task=false 且无草稿匹配 → 调用 SyncService.sync()（原有逻辑不变）

## 6. 测试

- [x] 6.1 单测 `TaskDetectionService`：普通笔记不触发任务、周期任务识别、一次性任务识别、AI 响应异常降级
- [x] 6.2 单测 `TaskDraftService`：草稿创建、字段填入、完成判断、超时废弃
- [x] 6.3 单测 `TaskNotionService`：Tasks DB 懒初始化、已有 DB 复用、对话历史追加
- [x] 6.4 集成测试消息路由：有草稿优先匹配、任务不写 Notes DB、普通笔记路径不变
