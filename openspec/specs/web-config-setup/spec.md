## ADDED Requirements

### Requirement: Notion Token 验证接口
系统 SHALL 提供 `POST /api/notion/validate-token` 接口，接收 `{ notionToken: string }`，用该 token 调用 Notion search API（`filter.value = "database"`），返回可访问的数据库列表。接口需要 JWT 鉴权。

#### Scenario: Token 有效返回数据库列表
- **WHEN** 请求携带有效 JWT，body 中的 notionToken 有权访问至少一个数据库
- **THEN** 返回 HTTP 200，body 为 `{ databases: [{ id, title }] }`

#### Scenario: Token 无效返回 400
- **WHEN** 请求携带有效 JWT，但 notionToken 被 Notion 拒绝（401/403）
- **THEN** 返回 HTTP 400，body 包含 `{ error: "invalid_token" }`

#### Scenario: Token 有效但无可访问数据库
- **WHEN** 请求携带有效 JWT，notionToken 有效但没有任何数据库权限
- **THEN** 返回 HTTP 200，body 为 `{ databases: [] }`

---

### Requirement: Setup 向导三步流程
前端 SHALL 在路由 `/setup` 提供三步配置向导：
- **Step 1**：输入 Notion Integration Token，点击"验证"后调用 `/api/notion/validate-token`
- **Step 2**：从返回的数据库列表中选择目标数据库（下拉列表展示数据库标题）
- **Step 3**：点击"保存配置"调用 `PUT /api/user/config`，成功后跳转 `/dashboard`

步骤间状态在组件内保持，不使用全局 store。

#### Scenario: Token 验证通过进入 Step 2
- **WHEN** 用户在 Step 1 输入有效 token 并点击"验证"
- **THEN** 页面进入 Step 2，显示该 token 可访问的数据库下拉列表

#### Scenario: Token 验证失败显示错误
- **WHEN** 用户在 Step 1 输入无效 token 并点击"验证"
- **THEN** 显示错误提示，停留在 Step 1

#### Scenario: 选择数据库后保存
- **WHEN** 用户在 Step 2 选择一个数据库，点击"保存配置"
- **THEN** 调用 `PUT /api/user/config`，成功后跳转 `/dashboard`

#### Scenario: 未配置用户自动跳转 Setup
- **WHEN** 已登录用户（有有效 JWT）访问 `/dashboard` 或 `/`，但 `/api/user/me` 返回 404 或 status 非 ACTIVE
- **THEN** 自动跳转到 `/setup`

---

### Requirement: 保存配置接口
系统 SHALL 提供 `PUT /api/user/config` 接口，接收 `{ notionToken, databaseId }`，验证 token 有效性后将 notionToken AES 加密存储，databaseId 明文存储，更新 `user_config` 表（不存在则插入）。接口需要 JWT 鉴权，openId 从 JWT 中提取，不接受客户端传入。

#### Scenario: 保存配置成功
- **WHEN** 请求携带有效 JWT，notionToken 和 databaseId 均合法
- **THEN** user_config 中该 openId 的记录被更新/插入，返回 HTTP 200 和更新后的配置视图

#### Scenario: 禁止伪造 openId
- **WHEN** 请求 body 中包含 openId 字段
- **THEN** 该字段被忽略，实际使用 JWT 中的 sub 作为 openId

#### Scenario: notionToken 为空返回 400
- **WHEN** 请求 body 中 notionToken 为空或缺失
- **THEN** 返回 HTTP 400
