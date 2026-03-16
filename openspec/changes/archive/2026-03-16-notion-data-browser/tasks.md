## 1. 后端：扩展 BlockContentParser

- [x] 1.1 在 `BlockContentParser` 新增 `static String toMarkdown(JsonNode blocks)` 方法，支持 paragraph / heading_1/2/3 / bulleted_list_item / numbered_list_item / to_do / quote / code / callout / divider / image，未知类型降级提取 plain_text
- [x] 1.2 为 `toMarkdown()` 添加单元测试，覆盖段落、标题、列表、to_do、code、image 和未知类型降级场景

## 2. 后端：新增 NotionBrowserController

- [x] 2.1 新建 `NotionBrowserController`，实现 `GET /api/notion/pages?year=YYYY&month=MM`：从 JWT 取 openId，查 user_config 解密 token，调 `NotionService.queryDatabase()` 后端按 title 过滤，返回 `[{date, pageId}]` 升序
- [x] 2.2 实现 `GET /api/notion/pages/{pageId}/block-count`：调 `retrieveBlockChildren`，返回 `{blockCount: N}`，异常时返回 `{blockCount: 0}`
- [x] 2.3 实现 `GET /api/notion/pages/{pageId}/content`：调 `retrieveBlockChildren` → `BlockContentParser.toMarkdown()`，同时调 `getPageProperty(apiKey, pageId, "Description")` 取 aiSummary，返回 `{markdown, aiSummary}`；pageId 无效返回 404
- [x] 2.4 确认三个接口已在 SecurityConfig 的 `/api/**` 路径下受 JWT 鉴权保护（无需额外配置，确认即可）

## 3. 前端：安装依赖与路由

- [x] 3.1 在 `frontend/` 目录下 `npm install marked`，并更新 `package.json`
- [x] 3.2 在 `router/index.ts` 新增 `/notion` 路由，指向 `NotionView.vue`，需要 JWT 鉴权（已有 config 才能访问）
- [x] 3.3 在 `App.vue` 导航栏新增「记录」链接，指向 `/notion`

## 4. 前端：NotionView.vue 热力图

- [x] 4.1 创建 `views/NotionView.vue`，实现月份头部（年月显示 + 上/下月切换按钮）和周标题行（Mo Tu We Th Fr Sa Su）
- [x] 4.2 实现热力图网格：计算当月日期格（含首行前补空格），每格显示日期数字，颜色按 blockCount 分 5 级
- [x] 4.3 实现两阶段加载：切换月份时先调 `/api/notion/pages` 渲染第一阶段（有/无页面），再并发（最多 5 个）调 `/api/notion/pages/{pageId}/block-count` 逐步更新颜色；月份切换加防抖/取消逻辑
- [x] 4.4 实现格子点击：有页面则加载 `/api/notion/pages/{pageId}/content`，无页面显示「该天暂无记录」

## 5. 前端：NotionView.vue 详情面板

- [x] 5.1 实现详情面板标题（选中日期）和「今日记录」区块，用 `marked` 渲染 markdown 字符串为 HTML（注意 XSS，使用 marked 的 sanitize 或 DOMPurify）
- [x] 5.2 实现「AI 日报」可折叠区块：aiSummary 非空时显示，默认折叠，点击展开/收起
- [x] 5.3 实现响应式布局：移动端热力图和详情面板上下堆叠，PC 端（≥768px）左右并排
