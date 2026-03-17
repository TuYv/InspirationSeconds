## Context

image.soloship.top 是一个全新的独立产品。技术上可复用 soloship 现有的 Spring Boot 基础设施（MySQL、Nginx、部署流程），但业务逻辑完全独立。前端是新的 Vite + Vue 3 项目，后端是轻量的主题 CRUD 服务。

核心技术挑战：
1. **CSS 隔离** — 用户自定义 CSS 不能破坏 app 自身 UI
2. **图片导出** — 前端渲染转 PNG，质量与兼容性
3. **缩略图生成** — 发布时截图，避免服务端 headless browser

## Goals / Non-Goals

**Goals:**
- markdown 实时渲染预览，多种 canvas 尺寸
- 前端导出 PNG（2x 清晰度）
- CSS 主题编辑器，自动 scope 隔离
- 主题画廊，支持浏览、搜索、一键应用
- 匿名发布主题，发布时生成缩略图
- 10 个内置预置主题（冷启动）

**Non-Goals:**
- 用户账号系统（当前阶段）
- 服务端渲染图片
- markdown 扩展（数学公式、自定义块等）
- 与 InspirationSeconds 日签图片集成（未来）
- 主题付费/变现机制

## Decisions

### D1: CSS Scoping — 选择器前缀注入

**决策**: 用户 CSS 通过正则解析，自动在每个选择器前插入 `#preview-root`。

```
用户写:  h1 { color: red; }
实际变成: #preview-root h1 { color: red; }
```

**替代方案**:
- Shadow DOM — html2canvas 对 shadow DOM 支持不稳定，排除
- iframe sandbox — 工程复杂，跨 frame 截图困难，排除

**风险**: 正则解析 CSS 不完美，`@media`、`@keyframes` 等 at-rules 需要特殊处理

---

### D2: 图片导出 — 纯前端 html2canvas

**决策**: 使用 html2canvas，scale: 2，纯浏览器端导出。

**替代方案**:
- Playwright 服务端渲染 — 需要服务器资源，部署复杂，排除
- wkhtmltoimage — 服务端 CLI 工具，依赖重，排除

**风险**: html2canvas 对某些 CSS 属性（`backdrop-filter`、部分渐变）支持不完整；通过限制主题 CSS 能力范围来规避

---

### D3: 缩略图生成 — 发布时浏览器截图

**决策**: 用户点击"发布"时，前端先用 html2canvas 截 preview 区，转 base64，和主题数据一起 POST 到后端。

**优势**: 服务端零额外依赖，截图质量与用户实际看到的一致

**后端存储**: 接收 base64，解码写本地文件（或转存 OSS），返回 URL。图片统一放 `/data/image-thumbnails/`

---

### D4: 后端部署方式 — 独立 Spring Boot 模块 vs 现有项目新增 module

**决策**: 在现有 InspirationSeconds Spring Boot 项目中新增 Maven module `image-service`，共享 MySQL datasource 配置，Nginx 按路径路由。

**理由**: 复用现有部署流程（docker compose）、MySQL、Nginx，避免重复维护基础设施

**路由**:
```
image.soloship.top     → image-frontend (静态文件)
image.soloship.top/api → image-service (Spring Boot, port 8081)
```

---

### D5: 前端项目结构

```
image-frontend/
  src/
    views/
      EditorView.vue      # / 主编辑器
      GalleryView.vue     # /themes 画廊
      ThemeEditorView.vue # /themes/edit 主题编辑器
    components/
      MarkdownPreview.vue # 预览容器（含 #preview-root）
      CssEditor.vue       # CodeMirror CSS 编辑器
      MarkdownEditor.vue  # CodeMirror markdown 编辑器
    utils/
      cssScope.ts         # CSS 选择器前缀注入
      exportImage.ts      # html2canvas 封装
```

## Risks / Trade-offs

| 风险 | 缓解方案 |
|------|----------|
| html2canvas 不支持某些 CSS 特性 | 主题编辑器提示已知不支持的属性，文档说明 |
| CSS 选择器注入正则解析 bug | 限制主题 CSS 不使用 at-rules（初版），后续迭代 |
| 缩略图 base64 POST 体积过大 | 限制 preview 容器最大尺寸 600px，截图前压缩质量 0.8 |
| 恶意 CSS 注入（XSS via CSS） | CSS 不执行 JS，`expression()` 等老特性现代浏览器已禁用；content 属性可过滤 |

## Migration Plan

1. 新建 `image-frontend/` 和 `image-service/` 目录
2. MySQL 新增 `themes` 表（不影响现有表）
3. docker-compose 新增 image-service 容器
4. Nginx 新增 image.soloship.top vhost 配置
5. 预置 10 个内置主题（SQL seed）

回滚：删除 Nginx vhost 即可，不影响主服务。

## Open Questions

- 缩略图文件存本地还是 OSS？当前选本地，后期流量大了再迁 OSS
- star（点赞）是否防重复？匿名场景下用 localStorage 记录已点赞，简单处理
