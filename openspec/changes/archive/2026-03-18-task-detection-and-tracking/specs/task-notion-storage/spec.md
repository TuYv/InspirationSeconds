## ADDED Requirements

### Requirement: 每个用户拥有独立的 Notion Tasks Database
系统 SHALL 在用户首次创建任务时，自动在其 Notion 空间下创建独立的 Tasks Database，并将 Database ID 写入 `user_config.tasks_database_id`。Tasks Database 与 Notes Database 完全解耦，位于不同的 Notion Database。

#### Scenario: 首次创建任务时自动初始化 Tasks Database
- **WHEN** 用户第一次创建任务且 `user_config.tasks_database_id` 为空
- **THEN** 系统调用 Notion API 创建 Tasks Database，回写 ID，再创建任务条目

#### Scenario: Tasks Database 已存在时直接使用
- **WHEN** `user_config.tasks_database_id` 非空
- **THEN** 系统直接在现有 Tasks Database 中创建任务条目，不重复创建

---

### Requirement: Tasks Database 包含规定字段
Notion Tasks Database SHALL 包含以下 Property 字段：`Name`（title）、`Type`（select: recurring/one_time）、`Cycle`（rich_text）、`Trigger`（rich_text）、`Progress`（rich_text）、`EndCondition`（rich_text）、`Status`（select: active/completed/abandoned/deleted）、`CreatedAt`（date）。

#### Scenario: 周期任务写入 Cycle 字段
- **WHEN** 创建类型为 recurring 的任务
- **THEN** Notion 任务页面的 Cycle 字段填入 AI 提取的周期描述，Trigger 字段留空

#### Scenario: 一次性任务写入 Trigger/Progress/EndCondition 字段
- **WHEN** 创建类型为 one_time 的任务
- **THEN** Notion 任务页面的 Trigger、Progress、EndCondition 字段填入对应值，Cycle 字段留空

---

### Requirement: 任务页面内追加对话历史
系统 SHALL 在 Notion 任务页面正文中追加完整对话历史（含草稿阶段的追问对话），格式为 append-only blocks，每条含时间戳和角色标识。

#### Scenario: 草稿阶段对话在创建时一并写入
- **WHEN** 任务从 PENDING 草稿状态完成创建
- **THEN** `task_draft.conversation_log` 中的所有历史对话写入 Notion 任务页面，草稿记录删除

#### Scenario: 对话格式可读
- **WHEN** 查看 Notion 任务页面
- **THEN** 每条对话以 "🧑 [时间] 内容" 或 "🤖 [时间] 内容" 格式呈现，按时间顺序排列

---

### Requirement: 任务支持四种状态，deleted 为软删除
系统 SHALL 支持任务状态：`active`、`completed`、`abandoned`、`deleted`。`deleted` 状态仅更新 Notion Status 属性，不物理删除任务页面及其对话历史。

#### Scenario: 软删除保留数据
- **WHEN** 任务状态变更为 deleted
- **THEN** Notion 任务页面存在，Status 属性为 deleted，对话历史完整保留

#### Scenario: 放弃与删除语义区分
- **WHEN** 用户主动说放弃某任务
- **THEN** 状态置为 abandoned（有主观意愿的结束）
- **WHEN** 用户说删除或不需要某任务
- **THEN** 状态置为 deleted（条目不再有效，但历史保留）
