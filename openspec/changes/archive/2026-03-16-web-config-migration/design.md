## Context

当前系统用微信聊天框作为配置入口，状态机（`ConfigFlowService`）驱动用户分步提交 Notion token 和 database ID。网页端（`App.vue`）是单页面，只能读取配置。OAuth 回调目前将 openId 直接拼在 URL query 中返回前端，无鉴权保护。

后端无 Spring Security 配置，所有接口均可匿名访问。技术栈：Spring Boot 2.7.18 / Java 17，无现有 JWT 依赖。

## Goals / Non-Goals

**Goals:**
- 引入 JWT 鉴权，OAuth 完成后签发 token，保护写入接口
- 前端引入 Vue Router，支持 `/setup`、`/dashboard`、`/settings` 三个页面
- Setup 向导：输入 Notion token → 验证 → 列出可访问数据库 → 选择 → 保存
- Dashboard：展示配置摘要
- Settings：修改 token 或数据库

**Non-Goals:**
- Notion 数据浏览（Change 2）
- AI 日报历史（Change 2）
- Admin 多用户管理视图
- 移除或重构 `ConfigFlowService`（标记 deprecated 即可）

## Decisions

### D1: JWT 签发时机与传递方式

**决策**：OAuth 回调（`/wx/oauth/callback`）和 QR 扫码成功（`/wx/oauth/qr/status`）均签发 JWT，通过 URL fragment 或 query 参数回传前端，前端存入 `localStorage`。

**理由**：后端 redirect 无法设置响应 header，只能通过 URL 传 token。使用 query 参数（`?token=xxx`）是最简单方案；fragment（`#token=xxx`）不会发送到服务器但兼容性略差。

**选择 query 参数**，前端收到后立即从 URL 中移除（`history.replaceState`），减少泄露风险。

**备选方案**：OAuth 后落地到后端生成的中间页，通过 `postMessage` 传 token — 过于复杂，不值得。

---

### D2: Spring Security 配置策略

**决策**：引入 `spring-boot-starter-security`，关闭默认 Basic Auth，配置 permit-all 白名单 + JWT filter。

白名单路径：
```
/wx/**          微信推送、OAuth 流程
/api/configs/** 现有只读接口（暂时保留匿名）
GET /api/user/me  读取自身配置（需要 JWT）
PUT /api/user/config  写入配置（需要 JWT）
POST /api/notion/validate-token  验证 token（需要 JWT）
GET /api/notion/databases  列出数据库（需要 JWT）
```

**理由**：引入 Security 后默认拦截所有请求，必须显式放行微信相关路径，否则 WeChat 推送校验会失败。

---

### D3: JWT 库选择

**决策**：使用 `io.jsonwebtoken:jjwt-api` + `jjwt-impl` + `jjwt-jackson`（版本 0.11.5，与 Spring Boot 2.7.x 兼容）。

**理由**：jjwt 是 Java 生态最常用的 JWT 库，API 简洁，无需引入 Spring Security OAuth2 全套依赖。

JWT payload：`{ sub: openId, iat, exp }`，默认有效期 30 天。

---

### D4: Notion token 验证 + 数据库列表

**决策**：新增 `POST /api/notion/validate-token` 接口，前端提交 `{ notionToken }` 后，后端用该 token 调用 Notion `POST /v1/search`（`filter.value = "database"`），返回数据库列表。成功即代表 token 有效。

**理由**：Notion 没有独立的 token 验证接口，search API 是最自然的验证手段，同时能返回数据库列表，一次调用完成两件事。

后端不在此步骤持久化 token，只做验证并返回结果，持久化在用户点击"保存"时（`PUT /api/user/config`）发生。

---

### D5: 前端路由与鉴权守卫

**决策**：
- 无 JWT → 走 OAuth/QR 流程（复用现有逻辑）
- 有 JWT，无配置 → 跳转 `/setup`
- 有 JWT，有配置 → 跳转 `/dashboard`
- `/settings` 需要有 JWT 且有配置

路由守卫在 `useAuth` composable 中集中管理，`App.vue` 改为纯路由出口。

---

### D6: QR 扫码流程适配 JWT

**决策**：`/wx/oauth/qr/status` 在 status=SUCCESS 时，除返回 `openId` 外，额外返回签发的 `token` 字段。前端 polling 收到 SUCCESS 后直接使用 `token`，不再依赖 openId。

**理由**：QR 流程是 PC 端主要登录方式，必须与 OAuth 流程保持对等的安全性。

## Risks / Trade-offs

**[风险] JWT 存 localStorage 有 XSS 风险** → 缓解：项目前端无第三方 JS 注入，Vue 模板自动转义，风险可接受。如未来有需要可改 httpOnly cookie。

**[风险] /wx/callback redirect 带 token 参数在浏览器历史/日志中可见** → 缓解：前端收到后立即 `history.replaceState` 清除 URL 参数。

**[风险] Spring Security 引入后可能影响现有微信推送校验** → 缓解：`/wx/**` 必须在 Security 配置中 permit-all，并关闭 CSRF（微信推送是 POST 无 CSRF token）。

**[Trade-off] 现有 `/api/configs/**` 暂时保持匿名可访问** → 接受：避免引入 Security 后破坏现有调试接口，后续单独加固。

## Migration Plan

1. 后端：引入 Spring Security + jjwt 依赖
2. 后端：实现 `JwtUtil`、`SecurityConfig`（permit-all 白名单）、`JwtAuthFilter`
3. 后端：修改 `WxOAuthController.callback` 签发 JWT，`qrStatus` 返回 JWT
4. 后端：新增 `UserController`（`/api/user/me`、`PUT /api/user/config`）
5. 后端：新增 `NotionValidationController`（`POST /api/notion/validate-token`）
6. 前端：安装 `vue-router`，创建路由配置
7. 前端：实现 `useAuth` composable
8. 前端：重构 `App.vue` 为路由出口
9. 前端：实现 `SetupView`、`DashboardView`、`SettingsView`

回滚：Security 配置通过 `@ConditionalOnProperty` 或 feature flag 控制，紧急时可禁用。

## Open Questions

- JWT 有效期 30 天是否合适？（目前无刷新 token 机制，过期需重新 OAuth）
- `PUT /api/user/config` 是否需要重新验证 Notion token？（建议是：保存时再验证一次）
