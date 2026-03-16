## Why

网页端当前的品牌名、文案、配色均为「Notion Config Console」定位，与产品名「灵感妙记」不符，给用户留下「工具后台」印象而非「个人灵感空间」。趁功能基本稳定，统一视觉语言和用户体验。

## What Changes

- **删除** `DashboardView.vue`（概览页），账号信息并入设置页
- **修改** 路由：`/` 重定向 `/notion`，移除 `/dashboard` 路由，导航去掉「概览」链接
- **修改** 所有跳转 `/dashboard` → `/notion`（SetupView、SettingsView 保存成功后）
- **修改** 品牌名：导航、登录页 tag 均改为「灵感妙记」
- **修改** 登录页、Setup、Settings 文案，换成「记录/回顾/连接」基调
- **修改** `SettingsView.vue`：顶部新增只读账号卡片（头像、昵称、状态、应用类型、访客标识、DB ID + 复制），wizard 在卡片下方
- **修改** `NotionView.vue`：新增 `monthLoading` 状态，右侧面板加载中显示友好文案，加载完成后才显示空态提示
- **修改** `styles.css`：色彩系统换为暖米底 + 绿主色（`--accent-2: #2d7a4a`，背景渐变改为暖绿/薄荷）

## Capabilities

### New Capabilities

<!-- 无新 capability，均为现有功能的视觉/文案/结构调整 -->

### Modified Capabilities

- `notion-heatmap`: 右侧面板新增 monthLoading 加载态，改变空态展示逻辑

## Impact

- **前端**：修改 `App.vue`、`router/index.ts`、`styles.css`、`SetupView.vue`、`SettingsView.vue`、`NotionView.vue`；删除 `DashboardView.vue`
- **后端**：无变更
- **依赖**：无新增
