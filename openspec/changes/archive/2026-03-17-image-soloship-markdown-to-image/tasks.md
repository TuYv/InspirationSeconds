## 1. 后端：项目结构与数据库

- [x] 1.1 在现有项目中新建 Maven module `image-service`，配置独立端口 8081
- [x] 1.2 新增 `themes` 表 DDL（id, name, description, css, preview_md, author_name, thumbnail_url, star_count, created_at）
- [x] 1.3 创建 `Theme` 实体类和 `ThemeRepository`（MyBatis-Plus）
- [x] 1.4 配置本地缩略图存储目录 `/data/image-thumbnails/`，挂载到 Docker volume

## 2. 后端：主题 API

- [x] 2.1 实现 `GET /api/themes` — 分页、排序（star_count/created_at）、关键词搜索
- [x] 2.2 实现 `GET /api/themes/:id` — 返回含 css 字段的完整主题
- [x] 2.3 实现 `POST /api/themes` — 接收 base64 缩略图，解码写文件，插入数据库，返回 201
- [x] 2.4 实现 `POST /api/themes/:id/star` — star_count + 1
- [x] 2.5 预置 10 个内置主题的 SQL seed 数据（覆盖简约/暗黑/温暖/商务等风格）

## 3. 前端：项目初始化

- [x] 3.1 新建 `image-frontend/` 目录，初始化 Vite + Vue 3 + TypeScript 项目
- [x] 3.2 安装依赖：`@codemirror/lang-markdown`、`@codemirror/lang-css`、`markdown-it`、`html2canvas`
- [x] 3.3 配置 Vue Router：`/`、`/themes`、`/themes/edit`
- [x] 3.4 配置 Vite 开发代理：`/api` → `localhost:8081`

## 4. 前端：工具函数

- [x] 4.1 实现 `cssScope.ts` — 将用户 CSS 选择器自动加 `#preview-root` 前缀（处理普通选择器、群组选择器，跳过 at-rules）
- [x] 4.2 实现 `exportImage.ts` — 封装 html2canvas，支持 scale:2 导出下载和 Clipboard API 复制
- [x] 4.3 为 `cssScope.ts` 编写单元测试（普通选择器、群组选择器、CSS 语法错误容错）

## 5. 前端：公共组件

- [x] 5.1 实现 `MarkdownPreview.vue` — 接收 markdown 字符串和 scoped CSS，渲染为 `#preview-root` 容器内的 HTML
- [x] 5.2 实现 `MarkdownEditor.vue` — CodeMirror 6 markdown 编辑器，emit input 事件
- [x] 5.3 实现 `CssEditor.vue` — CodeMirror 6 CSS 编辑器，支持语法高亮，emit input 事件

## 6. 前端：主编辑器页面 /

- [x] 6.1 实现 `EditorView.vue` 左右分栏布局
- [x] 6.2 接入主题选择器下拉（内置主题 + 从 localStorage 读取上次应用的画廊主题）
- [x] 6.3 实现 canvas 尺寸切换（1:1、4:5、16:9、9:16、自定义宽度）
- [x] 6.4 接入导出按钮（下载 PNG）和复制按钮（Clipboard API）
- [x] 6.5 预填示例 markdown 内容

## 7. 前端：主题画廊页面 /themes

- [x] 7.1 实现 `GalleryView.vue` 网格卡片布局（缩略图 + 名称 + 作者 + star 数）
- [x] 7.2 接入 `GET /api/themes` 分页加载（无限滚动或加载更多按钮）
- [x] 7.3 实现骨架屏加载占位
- [x] 7.4 实现排序切换（最热/最新）和关键词搜索（防抖 400ms）
- [x] 7.5 实现"应用"按钮：存入 localStorage，跳转主编辑器并应用
- [x] 7.6 实现 star 点赞：调用 API，localStorage 记录已点赞 ID 防重复

## 8. 前端：主题编辑器页面 /themes/edit

- [x] 8.1 实现 `ThemeEditorView.vue` 左右分栏（CSS 编辑器 + 实时预览）
- [x] 8.2 实现 fork 功能：URL 参数 `?fork={id}` 时从 API 加载该主题 CSS 预填编辑器
- [x] 8.3 实现发布流程：名称必填验证 → html2canvas 截图（1x JPEG 0.8）→ POST /api/themes → 成功提示

## 9. 基础设施

- [x] 9.1 更新 docker-compose.yml，新增 `image-service` 容器（port 8081，挂载 thumbnail volume）
- [x] 9.2 配置 Nginx：新增 `image.soloship.top` vhost，静态文件和 `/api` 反代
- [x] 9.3 更新部署脚本，支持 `image-frontend` 的构建和静态文件推送
