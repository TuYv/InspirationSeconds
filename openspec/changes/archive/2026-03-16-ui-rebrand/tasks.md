## 1. 色彩系统

- [x] 1.1 修改 `styles.css`：`--accent` → `#52a96e`，`--accent-2` → `#2d7a4a`；背景渐变左上改为 `#d4ead9`，右上改为 `#d6f0e8`；input focus 和 badge 默认色从蓝改为绿系

## 2. 路由与导航

- [x] 2.1 修改 `router/index.ts`：`/` 重定向到 `/notion`，移除 `/dashboard` 路由，新增 `/dashboard` → `/notion` 重定向保险
- [x] 2.2 修改 `App.vue` 导航栏：品牌名改为「灵感妙记」，移除「概览」链接

## 3. 登录页文案（App.vue）

- [x] 3.1 将登录页 hero-tag 改为「灵感妙记」，h1 改为「记录每一个闪过的念头」，副标题改为「在微信里随手记，AI 帮你整理和回顾」
- [x] 3.2 将授权卡片标题改为「微信授权登录」，说明文字改为「授权后即可开始记录，回顾你的灵感」

## 4. SetupView 文案与跳转

- [x] 4.1 页面 h1 改为「连接你的 Notion」，副标题改为「连接后，微信消息将自动同步到你的 Notion 数据库」
- [x] 4.2 Step 2 数据库选择 hint 改为「选择用于存储灵感记录的数据库」
- [x] 4.3 保存成功后跳转 `/dashboard` → `/notion`

## 5. SettingsView 重构

- [x] 5.1 在 `onMounted` 中调用 `GET /api/user/me` 加载账号信息，新增账号卡片区块（头像、昵称、状态 badge、应用类型、访客标识、DB ID + 复制按钮），展示在 wizard 上方
- [x] 5.2 页面 h1 改为「设置」，副标题改为「管理你的账号与 Notion 连接」；Step1 h2 改为「更换 Notion Token」
- [x] 5.3 wizard「取消」链接目标 `/dashboard` → `/notion`；保存成功后跳转 `/dashboard` → `/notion`

## 6. 删除 DashboardView

- [x] 6.1 删除 `frontend/src/views/DashboardView.vue`

## 7. NotionView 加载态优化

- [x] 7.1 新增 `monthLoading` ref，`loadMonth()` 开始时设为 `true`，`pageMap` 赋值后立即设为 `false`
- [x] 7.2 右侧面板新增 monthLoading 状态展示：加载中显示「✦ 正在加载你的灵感...」，加载完成无选中日期时显示「点击左侧日期查看当天记录」
