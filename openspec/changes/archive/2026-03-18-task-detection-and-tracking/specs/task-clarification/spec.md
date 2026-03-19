## ADDED Requirements

### Requirement: 字段缺失时向用户发起追问
系统 SHALL 在 AI 检测到任务但字段不完整时，生成自然语言追问并通过微信回复用户，同时创建 `task_draft` 草稿记录已收集字段和待收集字段列表。

#### Scenario: 缺少结束条件时追问
- **WHEN** AI 检测到任务且 `missing_fields` 包含 `end_condition`
- **THEN** 系统创建 `task_draft` 记录，回复用户如 "收到！请问这个任务的完成标准是什么？"

#### Scenario: 多字段缺失时逐步追问
- **WHEN** `missing_fields` 包含多个字段
- **THEN** 系统每次只追问一个字段（最重要的优先），不一次性列出所有问题

---

### Requirement: 用户同时可有多个待确认草稿
系统 SHALL 支持同一用户同时存在多个 `PENDING` 状态的 `task_draft`，不得因新草稿覆盖旧草稿。

#### Scenario: 连续发送两条任务消息
- **WHEN** 用户先发 "我要每天背单词"，再发 "我要三月底读完《原则》"，两条均需追问
- **THEN** 系统创建两条独立草稿，分别追问各自缺失字段

---

### Requirement: AI 语义匹配用户回复到对应草稿
系统 SHALL 在用户有多个 PENDING 草稿时，调用 AI 判断当前回复对应哪个草稿，再将信息填入对应草稿。

#### Scenario: 回复精确匹配单一草稿
- **WHEN** 用户回复 "背单词那个，目标是坚持30天"
- **THEN** AI 匹配到 "每天背单词" 草稿，将结束条件填入该草稿

#### Scenario: 回复无法匹配任何草稿
- **WHEN** 用户回复内容无法关联到任何 PENDING 草稿
- **THEN** 系统将该消息按普通笔记流程处理，不干扰现有草稿

---

### Requirement: 追问对话记录在草稿中
系统 SHALL 将草稿存续期间的所有对话（用户消息 + 系统追问）追加存储在 `task_draft.conversation_log` JSON 字段中。

#### Scenario: 对话历史随草稿保存
- **WHEN** 系统追问用户，用户回复
- **THEN** 双方消息均追加到 `conversation_log`，含时间戳和角色标识

---

### Requirement: 草稿超时自动废弃
系统 SHALL 对超过 7 天未更新的 PENDING 草稿自动标记为废弃，不再追问用户。

#### Scenario: 草稿超过7天无更新
- **WHEN** `task_draft.updated_at` 距今超过 7 天
- **THEN** 系统将草稿状态置为 `EXPIRED`，不写入 Notion，不再回复追问
