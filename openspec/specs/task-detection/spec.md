## Requirements

### Requirement: 每条文字消息经 AI 判断是否为任务
系统 SHALL 在文字消息写入 Notion 前，调用 AI 判断该消息是否包含任务意图。AI 判断应保守（宁可漏报不误报），普通笔记不得被误识别为任务。

#### Scenario: 普通笔记不触发任务流程
- **WHEN** 用户发送 "今天吃了好吃的拉面"
- **THEN** AI 返回 `is_task: false`，消息正常写入 Notion Notes Database

#### Scenario: 明确任务被识别
- **WHEN** 用户发送 "我要每天背50个单词"
- **THEN** AI 返回 `is_task: true`，进入任务处理流程，消息不写入 Notes Database

---

### Requirement: 任务分类为周期任务或一次性任务
系统 SHALL 将识别到的任务分类为 `recurring`（周期任务）或 `one_time`（一次性任务）。

#### Scenario: 周期任务识别
- **WHEN** 用户发送包含明确重复频率的消息（"每天"、"每周"、"每月"等）
- **THEN** AI 返回 `task_type: "recurring"` 并提取 `cycle` 字段

#### Scenario: 一次性任务识别
- **WHEN** 用户发送有明确截止或终点的消息（"三月底前读完《原则》"）
- **THEN** AI 返回 `task_type: "one_time"` 并尝试提取 `trigger`、`current_progress`、`end_condition`

---

### Requirement: AI 结构化输出任务字段
系统 SHALL 要求 AI 以 JSON 格式返回检测结果，包含 `is_task`、`task_type`、`extracted`、`missing_fields` 字段。

#### Scenario: 字段完整时不产生 missing_fields
- **WHEN** 消息中包含足够信息（名称、周期/条件、进度、结束条件均可推断）
- **THEN** `missing_fields` 返回空数组，任务直接进入创建流程

#### Scenario: 字段不足时列出缺失字段
- **WHEN** 消息中缺少当前进度或结束条件
- **THEN** `missing_fields` 包含对应字段名，触发追问流程

---

### Requirement: 检测前拉取用户现有 active 任务列表
系统 SHALL 在 AI 检测时，将用户当前 active 任务摘要作为上下文传入，以便 AI 判断消息是否为对现有任务的进度更新（而非新任务）。

#### Scenario: related_task_id 非空触发进度更新路由
- **WHEN** AI 检测返回 related_task_id 指向某 active 任务 ID
- **THEN** HandlerWxPortalService 直接路由到进度更新分支，跳过新任务检测

#### Scenario: related_task_id 为 null 时继续原有路由
- **WHEN** AI 检测返回 related_task_id 为 null
- **THEN** 继续原有判断逻辑（is_task → 新任务 / 终结意图 / 普通笔记）
