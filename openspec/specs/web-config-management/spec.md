## ADDED Requirements

### Requirement: 获取当前用户配置接口
系统 SHALL 提供 `GET /api/user/me` 接口，从 JWT 中提取 openId，返回该用户的配置视图（不含加密 token）。接口需要 JWT 鉴权。

#### Scenario: 已配置用户返回配置
- **WHEN** 请求携带有效 JWT，对应 openId 在 user_config 中存在
- **THEN** 返回 HTTP 200，body 包含 openId、appType、status、databaseId、isGuest、nickname、avatarUrl、updatedAt

#### Scenario: 未配置用户返回 404
- **WHEN** 请求携带有效 JWT，但对应 openId 在 user_config 中不存在
- **THEN** 返回 HTTP 404

---

### Requirement: Dashboard 配置摘要页
前端 SHALL 在路由 `/dashboard` 展示当前用户的配置摘要，包括：头像、昵称、配置状态 badge、数据库 ID（截断显示 + 复制按钮）、是否为访客账号、最后更新时间。页面顶部提供导航链接到 `/settings`。

#### Scenario: 已配置用户看到完整摘要
- **WHEN** 已登录且已配置用户访问 `/dashboard`
- **THEN** 页面显示用户头像、昵称、ACTIVE 状态 badge 和数据库 ID

#### Scenario: 数据库 ID 可复制
- **WHEN** 用户点击数据库 ID 旁的复制按钮
- **THEN** 数据库 ID 完整值被复制到剪贴板，按钮显示"已复制"反馈

---

### Requirement: Settings 配置修改页
前端 SHALL 在路由 `/settings` 提供配置修改界面，允许用户更换 Notion Integration Token 或更换数据库（复用 Setup 向导的两步流程）。修改保存成功后跳转回 `/dashboard`。

#### Scenario: 修改 token 重新选择数据库
- **WHEN** 用户在 Settings 页面输入新 token 并点击"验证"
- **THEN** 显示新 token 可访问的数据库列表，供用户重新选择

#### Scenario: 保存修改成功
- **WHEN** 用户完成 token 验证和数据库选择，点击"保存"
- **THEN** 调用 `PUT /api/user/config`，成功后跳转 `/dashboard`

#### Scenario: 取消修改返回 Dashboard
- **WHEN** 用户点击"取消"按钮
- **THEN** 跳转回 `/dashboard`，配置不变

---

### Requirement: 路由鉴权守卫
前端 SHALL 在所有路由上执行鉴权检查：无 JWT 时展示登录入口（OAuth/QR），有 JWT 但无配置时跳转 `/setup`，有 JWT 且有配置时允许访问 `/dashboard` 和 `/settings`。

#### Scenario: 未登录访问任意路由
- **WHEN** localStorage 中无 `wx_jwt`，用户访问任意页面
- **THEN** 显示登录界面（微信环境显示 OAuth 按钮，PC 端显示 QR 码）

#### Scenario: 已登录已配置直接进入 Dashboard
- **WHEN** localStorage 中有有效 `wx_jwt`，且 `/api/user/me` 返回 ACTIVE 配置
- **THEN** 自动跳转到 `/dashboard`
