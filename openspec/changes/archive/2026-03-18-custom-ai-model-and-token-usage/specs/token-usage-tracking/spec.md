## ADDED Requirements

### Requirement: 仅对用户自有 API Key 的调用记录 token 用量
系统 SHALL 在用户使用自己的 ai_api_key 发起 AI 调用后，将本次调用的 token 用量按天聚合写入 token_usage 表。使用系统全局 API Key 的调用不记录。

#### Scenario: 用户自有 Key 调用后记录用量
- **WHEN** 用户已配置 ai_api_key，AI 调用成功，响应包含 usage 字段
- **THEN** 当天 (open_id, usage_date) 记录的 prompt_tokens / completion_tokens / total_tokens / call_count 原子累加

#### Scenario: 系统 Key 调用不记录
- **WHEN** 用户未配置 ai_api_key，AI 调用走系统全局配置
- **THEN** token_usage 表不写入任何记录

#### Scenario: AI 调用失败时不记录
- **WHEN** AI 接口返回错误或响应不含 usage 字段
- **THEN** token_usage 表不写入，不抛异常

---

### Requirement: 按天聚合的 token 用量原子写入
系统 SHALL 使用 INSERT ... ON DUPLICATE KEY UPDATE 语句原子累加每日用量，避免并发写入问题。

#### Scenario: 同一天多次调用累加
- **WHEN** 同一用户同一天发生多次 AI 调用
- **THEN** token_usage 中该用户当天只有一行，各字段为所有调用之和，call_count 为调用次数

#### Scenario: 跨天自动新建记录
- **WHEN** 新的一天首次发生 AI 调用
- **THEN** 为该用户当天插入新行，从零开始累计

---

### Requirement: 前端独立页面展示 token 用量日明细
系统 SHALL 提供 `GET /api/user/token-usage` 接口，返回用户所有日期的 token 用量记录，前端在独立页面展示。

#### Scenario: 有历史用量时返回列表
- **WHEN** 已登录用户 GET /api/user/token-usage
- **THEN** 返回按日期倒序排列的列表，每条包含 usage_date / prompt_tokens / completion_tokens / total_tokens / call_count

#### Scenario: 无历史用量时返回空列表
- **WHEN** 用户从未使用自有 Key 调用 AI
- **THEN** 返回空数组 []

#### Scenario: 设置页入口跳转用量页
- **WHEN** 用户在设置页点击"查看 Token 用量"
- **THEN** 跳转至独立的 /token-usage 路由页面，页面展示日明细表格（日期 / 输入 / 输出 / 合计 / 调用次数）
