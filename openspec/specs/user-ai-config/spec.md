## Requirements

### Requirement: 用户可配置自定义 AI 模型
系统 SHALL 允许用户在设置页配置自己的 AI base_url、api_key 和 model。配置保存后，该用户的所有 AI 调用优先使用其自定义配置；未配置时 fallback 系统全局配置。

#### Scenario: 保存自定义 AI 配置
- **WHEN** 用户在设置页填写 base_url / api_key / model 并点击保存
- **THEN** api_key 经 AES 加密后存入 user_config，后续 AI 调用使用该配置

#### Scenario: 未配置时 fallback 系统配置
- **WHEN** 用户未填写 ai_api_key（字段为 NULL）
- **THEN** AI 调用使用系统全局 base_url / api_key / model，行为与之前相同

#### Scenario: 清除配置回到系统默认
- **WHEN** 用户将 api_key 保存为空字符串
- **THEN** 系统清除该用户的 AI 配置，恢复 fallback 系统配置

---

### Requirement: API Key 前端脱敏展示
系统 SHALL 在返回用户配置时对 ai_api_key 做脱敏处理，前端永远不得到明文 key。

#### Scenario: 脱敏展示已配置的 Key
- **WHEN** 前端调用 GET /api/user/me 且用户已配置 ai_api_key
- **THEN** 返回格式为 `sk-****...xxxx`（保留后4位，中间掩码），不返回明文

#### Scenario: 未配置时返回 null
- **WHEN** 前端调用 GET /api/user/me 且用户未配置 ai_api_key
- **THEN** ai_api_key 字段返回 null

---

### Requirement: 通过 PATCH 接口更新 AI 配置
系统 SHALL 提供 `PATCH /api/user/ai-config` 接口，单独更新 AI 模型配置，不影响 Notion 配置。

#### Scenario: 成功更新 AI 配置
- **WHEN** 已登录用户 PATCH /api/user/ai-config 携带有效 base_url / api_key / model
- **THEN** 配置更新成功，返回 200，ai_api_key 加密存储

#### Scenario: 部分字段更新
- **WHEN** 请求只携带 model 字段
- **THEN** 只更新 model，其他字段不变
