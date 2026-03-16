## ADDED Requirements

### Requirement: OAuth 完成后签发 JWT
系统 SHALL 在微信 OAuth 回调成功获取 openId 后，使用 `JWT_SECRET` 环境变量签发一个有效期为 30 天的 JWT（payload: `{ sub: openId }`），并将 token 以 query 参数 `?token=xxx` 的形式附加到 returnUrl 后进行重定向。

#### Scenario: OAuth 回调成功签发 JWT
- **WHEN** 微信 OAuth 回调收到有效 code，成功换取 openId
- **THEN** 重定向 URL 中包含 `token` query 参数，值为合法的 JWT 字符串

#### Scenario: OAuth 回调失败不签发 JWT
- **WHEN** 微信 OAuth 回调 code 无效或换取失败
- **THEN** 重定向 URL 中包含 `error` query 参数，不包含 `token`

---

### Requirement: QR 扫码登录返回 JWT
系统 SHALL 在 `/wx/oauth/qr/status` 接口中，当 status=SUCCESS 时，额外返回签发的 `token` 字段。

#### Scenario: QR 扫码成功返回 token
- **WHEN** 客户端轮询 `/wx/oauth/qr/status`，服务端 status 为 SUCCESS
- **THEN** 响应 JSON 包含 `token` 字段（合法 JWT）和 `openId` 字段

#### Scenario: QR 未完成不返回 token
- **WHEN** 客户端轮询 `/wx/oauth/qr/status`，服务端 status 为 PENDING 或 EXPIRED
- **THEN** 响应 JSON 不包含 `token` 字段

---

### Requirement: JWT 鉴权 Filter 验证请求
系统 SHALL 对 `/api/user/**` 和 `/api/notion/**` 路径的所有请求执行 JWT 校验。请求须在 `Authorization: Bearer <token>` header 中携带有效 JWT，否则返回 401。

#### Scenario: 合法 JWT 通过校验
- **WHEN** 请求携带有效且未过期的 JWT Bearer token
- **THEN** 请求正常处理，`SecurityContext` 中设置以 openId 为 principal 的认证信息

#### Scenario: 缺少 JWT 返回 401
- **WHEN** 请求 `/api/user/me` 未携带 Authorization header
- **THEN** 返回 HTTP 401，不处理业务逻辑

#### Scenario: 过期 JWT 返回 401
- **WHEN** 请求携带已过期的 JWT
- **THEN** 返回 HTTP 401

#### Scenario: 微信推送路径不受 JWT 拦截
- **WHEN** 微信服务器向 `/wx/portal` 发送 POST 请求（无 Authorization header）
- **THEN** 请求正常处理，不返回 401

---

### Requirement: 前端存储并携带 JWT
前端 SHALL 在收到 OAuth/QR 回调中的 `token` 参数后，将其存入 `localStorage`（key: `wx_jwt`），并在收到后立即通过 `history.replaceState` 从 URL 中移除该参数。后续所有 API 请求须在 `Authorization: Bearer <token>` header 中携带该 token。

#### Scenario: 收到 token 后清理 URL
- **WHEN** 页面 URL 包含 `?token=xxx` 参数
- **THEN** token 存入 localStorage，URL 中的 token 参数被移除，地址栏不再显示 token

#### Scenario: API 请求携带 Bearer token
- **WHEN** 前端发起任何需要鉴权的 API 请求
- **THEN** 请求 header 包含 `Authorization: Bearer <jwt>`
