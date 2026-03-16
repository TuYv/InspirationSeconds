## 1. 后端：JWT 基础设施

- [x] 1.1 在 `pom.xml` 中添加 `jjwt-api`、`jjwt-impl`、`jjwt-jackson`（版本 0.11.5）和 `spring-boot-starter-security` 依赖
- [x] 1.2 实现 `JwtUtil`：签发 JWT（sub=openId，exp=30天）、解析验证 JWT，secret 从 `JWT_SECRET` 环境变量读取
- [x] 1.3 实现 `JwtAuthFilter`（`OncePerRequestFilter`）：从 `Authorization: Bearer` header 提取并验证 JWT，写入 `SecurityContext`
- [x] 1.4 实现 `SecurityConfig`（`WebSecurityConfigurerAdapter`）：关闭 CSRF、关闭 Basic Auth、配置 `/wx/**` 和 `/api/configs/**` 为 permit-all，其余 `/api/**` 需要认证，注册 `JwtAuthFilter`
- [x] 1.5 在 `application.yml` 中添加 `jwt.secret` 占位配置，在 `.env` 示例中添加 `JWT_SECRET` 说明

## 2. 后端：修改 OAuth 流程签发 JWT

- [x] 2.1 修改 `WxOAuthController.callback`：成功获取 openId 后调用 `JwtUtil` 签发 JWT，将 `token` 参数替换原来的 `openId` 参数附加到 returnUrl 重定向
- [x] 2.2 修改 `WxOAuthController.qrStatus`：status=SUCCESS 时额外签发 JWT 并在响应 JSON 中返回 `token` 字段

## 3. 后端：用户配置接口

- [x] 3.1 新增 `UserController`，实现 `GET /api/user/me`：从 `SecurityContext` 获取 openId，查询 `user_config`，返回 `UserConfigView`（404 if not found）
- [x] 3.2 在 `UserController` 中实现 `PUT /api/user/config`：接收 `{ notionToken, databaseId }`，openId 从 JWT 提取，AES 加密 notionToken 后 upsert `user_config`，返回更新后的 `UserConfigView`

## 4. 后端：Notion Token 验证接口

- [x] 4.1 新增 `NotionValidationController`，实现 `POST /api/notion/validate-token`：接收 `{ notionToken }`，调用 Notion search API（`POST https://api.notion.com/v1/search`，filter type=database），返回 `{ databases: [{ id, title }] }` 或 400 错误

## 5. 前端：路由和鉴权基础

- [x] 5.1 安装 `vue-router@4`（`npm install vue-router@4`）
- [x] 5.2 创建 `src/router/index.ts`：定义 `/`、`/setup`、`/dashboard`、`/settings` 路由，配置路由守卫（无 JWT → 登录，有 JWT 无配置 → `/setup`，否则 → `/dashboard`）
- [x] 5.3 实现 `src/composables/useAuth.ts`：JWT 存取（`wx_jwt`）、解析 JWT payload、登录状态判断、退出登录
- [x] 5.4 重构 `App.vue`：改为 `<router-view>` 出口 + 顶部导航栏（已登录状态显示昵称和"设置"链接）
- [x] 5.5 修改 `main.ts`：注册 vue-router

## 6. 前端：OAuth/QR 登录适配 JWT

- [x] 6.1 修改登录逻辑（原 `App.vue` 中的 `onMounted`）：检测 URL 中的 `token` 参数，存入 localStorage `wx_jwt`，并 `history.replaceState` 清除 URL 参数
- [x] 6.2 修改 QR 扫码轮询逻辑：收到 SUCCESS 后使用响应中的 `token` 字段存入 localStorage，触发路由跳转
- [x] 6.3 创建 `src/utils/api.ts`：封装 fetch，自动在请求中注入 `Authorization: Bearer` header

## 7. 前端：Setup 向导页

- [x] 7.1 创建 `src/views/SetupView.vue`：三步向导骨架（步骤指示器 + 内容区域）
- [x] 7.2 实现 Step 1：Notion token 输入框 + "验证"按钮，调用 `POST /api/notion/validate-token`，成功后进入 Step 2
- [x] 7.3 实现 Step 2：数据库下拉列表（由 validate-token 返回结果填充），用户选择后进入 Step 3 确认
- [x] 7.4 实现 Step 3/保存：调用 `PUT /api/user/config`，成功后路由跳转 `/dashboard`

## 8. 前端：Dashboard 主页

- [x] 8.1 创建 `src/views/DashboardView.vue`：挂载时调用 `GET /api/user/me` 获取配置
- [x] 8.2 实现配置摘要展示：头像、昵称、状态 badge、数据库 ID（截断 + 复制按钮）、访客标记、最后更新时间
- [x] 8.3 添加"修改配置"按钮跳转 `/settings`

## 9. 前端：Settings 修改页

- [x] 9.1 创建 `src/views/SettingsView.vue`：复用 Setup 向导的两步流程（token 验证 + 数据库选择）
- [x] 9.2 实现"取消"按钮跳转 `/dashboard`
- [x] 9.3 保存成功后跳转 `/dashboard` 并显示成功提示

## 10. 验收

- [x] 10.1 微信内打开页面：OAuth 流程完成后能跳转 `/dashboard` 并显示配置
- [x] 10.2 PC 端：QR 扫码成功后自动跳转 `/dashboard`
- [x] 10.3 未配置用户（新用户）：登录后自动进入 `/setup` 向导，完成配置后跳转 `/dashboard`
- [x] 10.4 已配置用户进入 `/settings` 修改 token 和数据库，保存后 `/dashboard` 显示更新结果
- [x] 10.5 直接访问 `/wx/portal` POST（微信推送）：不受 JWT filter 拦截，正常处理
