## Why

用户目前必须通过微信聊天框完成 Notion 配置（粘贴 token、输入 database ID），流程割裂且体验差。将配置迁移到网页端，可提供更直观的引导和更安全的鉴权机制。

## What Changes

- **新增** JWT 鉴权体系：OAuth 完成后后端签发 JWT，所有写入接口需携带 Bearer token
- **新增** Setup 向导页：引导未配置用户完成 Notion token 输入、数据库列表选择、配置保存
- **新增** Dashboard 主页：展示当前配置摘要（头像、昵称、状态、数据库名）
- **新增** Settings 页：允许用户修改已有配置（更换 token 或数据库）
- **新增** 后端接口：`GET /api/user/me`、`PUT /api/user/config`、`POST /api/notion/validate-token`
- **修改** WxOAuthController：OAuth 完成后签发 JWT 并回传前端
- **修改** 前端 App.vue：改为 Vue Router 路由出口，支持多页面
- **废弃（渐进）** ConfigFlowService：微信聊天框配置流程标记为 deprecated，暂时保留

## Capabilities

### New Capabilities

- `jwt-auth`: 基于 JWT 的用户身份鉴权，OAuth 完成后签发，保护写入接口
- `web-config-setup`: 网页端 Setup 向导，支持 Notion token 验证 + 数据库列表选择 + 配置保存
- `web-config-management`: Dashboard 配置摘要展示 + Settings 配置修改页

### Modified Capabilities

<!-- 无现有 spec，不适用 -->

## Impact

- **后端**：新增 `JwtUtil`、`JwtAuthFilter`、`UserController`、`NotionValidationController`；修改 `WxOAuthController`
- **前端**：引入 `vue-router`；新增 `SetupView`、`DashboardView`、`SettingsView`、`useAuth` composable；重构 `App.vue`
- **依赖**：后端新增 `jjwt` 依赖；前端新增 `vue-router`
- **环境变量**：新增 `JWT_SECRET`（32 位以上随机字符串）
- **数据库**：无 schema 变更，使用现有 `user_config` 表
