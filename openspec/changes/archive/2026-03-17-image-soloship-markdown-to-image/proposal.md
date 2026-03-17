## Why

InspirationSeconds 在探索日签图片生成时发现：将 markdown 渲染成漂亮图片是一个独立的通用需求，现有工具（Carbon、Poet.so）要么场景单一、要么不支持自定义样式。在 image.soloship.top 做一个专注于此的独立产品，同时引入 CSS 主题社区作为差异化护城河。

## What Changes

这是一个全新的独立产品，不修改现有 InspirationSeconds 代码：

- **新建** `image-frontend/` — Vue 3 + Vite 前端项目，包含三个页面（主编辑器、画廊、主题编辑器）
- **新建** `image-backend/` — 独立 Spring Boot 模块，提供主题 CRUD API
- **新增** MySQL `themes` 表，存储社区主题和缩略图
- **新增** 静态文件存储（本地或 OSS），存放发布时生成的缩略图

## Capabilities

### New Capabilities

- `markdown-editor`: 左右分栏编辑器，左侧 markdown 输入，右侧实时 HTML 预览，支持多种 canvas 尺寸
- `image-export`: 前端 html2canvas 将预览区导出为 PNG，支持 2x 清晰度，可复制到剪贴板或下载
- `css-theme-editor`: 独立主题编辑器页面，CodeMirror CSS 编辑器 + 实时预览，CSS 自动 scope 到预览容器
- `theme-gallery`: 主题画廊，展示社区发布的主题，支持搜索、排序、一键应用
- `theme-publishing`: 匿名发布主题流程，发布时浏览器截图生成缩略图，随表单一起提交

### Modified Capabilities

## Impact

- **新增前端项目**: `image-frontend/`，独立 Vite 构建，部署到 image.soloship.top
- **新增后端模块**: `image-backend/`，或在现有 Spring Boot 项目中新增 module
- **数据库**: 新增 `themes` 表，不影响现有表结构
- **外部依赖**: CodeMirror 6, markdown-it, html2canvas
- **基础设施**: 新增子域名 image.soloship.top，Nginx 路由配置
