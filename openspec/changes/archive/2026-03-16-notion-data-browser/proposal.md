## Why

配置迁移完成后，网页端仍然只是管理工具。用户每天通过微信写下想法、同步到 Notion，却无法在网页上直接回顾这些内容。需要一个直观的数据浏览视图，让用户在网页上看到自己写了什么，以及 AI 对当天的总结。

## What Changes

- **新增** `GET /api/notion/pages` 接口：查询当月页面列表，返回日期与页面 ID
- **新增** `GET /api/notion/pages/{pageId}/content` 接口：返回页面正文 Markdown + AI 日报文本
- **新增** `GET /api/notion/pages/{pageId}/block-count` 接口：返回页面 block 数量（热力图着色用）
- **新增** 前端 `/notion` 页面：月历热力图 + 页面详情面板
- **扩展** `BlockContentParser`：新增 blocks → Markdown 转换方法
- **修改** `App.vue` 导航栏：添加"记录"链接

## Capabilities

### New Capabilities

- `notion-page-list`: 按月查询用户 Notion 数据库的页面列表，支持获取 block 数量
- `notion-page-viewer`: 读取页面正文 blocks 并转为 Markdown，同时返回 Description 属性（AI 日报）
- `notion-heatmap`: 前端月历热力图，颜色深浅按内容量分 5 级（浅绿→深绿），两阶段加载

### Modified Capabilities

<!-- 无现有 spec 需要修改 -->

## Impact

- **后端**：新增 `NotionBrowserController`；扩展 `BlockContentParser` 增加 `toMarkdown()` 方法
- **前端**：新增 `NotionView.vue`；安装 `marked` 依赖；修改 `App.vue` 导航
- **依赖**：前端新增 `marked`
- **数据库**：无 schema 变更
- **安全**：所有新接口需要 JWT 鉴权，Notion token 从 JWT → user_config → AES 解密获取，不经过前端
