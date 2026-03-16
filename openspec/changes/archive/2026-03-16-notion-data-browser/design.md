## Context

Change 1 已建立 JWT 鉴权体系。后端可通过 `SecurityContext` 获取 openId，再从 `user_config` 解密 Notion token。`NotionService` 已有 `retrieveBlockChildren()`、`queryDatabase()`、`getPageProperty()` 等方法可直接复用。`BlockContentParser.parse()` 已有 blocks → 纯文本逻辑，需扩展为 Markdown 输出。

## Goals / Non-Goals

**Goals:**
- 按月查询用户 Notion 数据库页面（每天一页，title = 日期字符串）
- 两阶段热力图：先显示有/无页面，再异步更新 block 数着色
- 页面详情：正文 Markdown + AI 日报（Description 属性）
- 绿色热力图（5级：`#f0f9f0` → `#a8d5b5` → `#52a96e` → `#2d7a4a` → `#1a4a2e`）

**Non-Goals:**
- Notion 写入/编辑
- block count 本地缓存
- 跨月统计/趋势分析
- summary_log 表

## Decisions

### D1: 页面列表查询策略

**决策**：调用 `NotionService.queryDatabase()` 查询全部页面，在后端按 title 过滤出目标月份（title 格式为 `YYYY-MM-DD`），返回 `[{date, pageId}]`。

**理由**：`queryDatabase()` 已实现分页，直接复用。Notion filter API 的 title 过滤是 `contains` 而非 `startsWith`，用 `2026-03` 过滤可能误匹配，所以后端自行过滤更可靠。实际每月数据量不超过 31 条，一次查完无分页压力。

---

### D2: block count 接口设计

**决策**：独立接口 `GET /api/notion/pages/{pageId}/block-count`，返回 `{ blockCount: N }`。前端在拿到页面列表后并行发起所有 block-count 请求。

**理由**：将 block count 合并到页面列表接口会让第一阶段变慢（需等所有页面的 block count）。独立接口让前端可以先渲染热力图骨架，再逐步更新颜色，体验更好。

---

### D3: blocks → Markdown 转换位置

**决策**：后端扩展 `BlockContentParser`，新增 `static String toMarkdown(JsonNode blocks)` 方法，覆盖常见 block 类型。前端只接收 Markdown 字符串，用 `marked` 渲染。

Markdown 转换覆盖的 block 类型：
```
paragraph, heading_1/2/3
bulleted_list_item, numbered_list_item
to_do, quote, code, callout, divider
image（转为 ![](url) 格式）
```

**理由**：Notion token 不应暴露给前端。后端统一处理转换逻辑，前端保持简单。

---

### D4: AI 日报获取

**决策**：在 `/content` 接口中，同时调用 `NotionService.getPageProperty(apiKey, pageId, "Description")` 获取 AI 日报文本，与 Markdown 正文一起返回：`{ markdown, aiSummary }`。

**理由**：AI 日报已由 `DailySummaryService` 写入 Notion 的 Description 属性，无需另建存储。一次接口调用拿到全部内容，减少前端请求数。

---

### D5: 热力图颜色分级

**决策**：按 blockCount 分 5 级：

| blockCount | 颜色 | 说明 |
|---|---|---|
| 0 | `#eef2ee` | 无内容 |
| 1–3 | `#a8d5b5` | 少量 |
| 4–8 | `#52a96e` | 中等 |
| 9–15 | `#2d7a4a` | 较多 |
| 16+ | `#1a4a2e` | 丰富 |

阈值可在前端常量中调整。

---

### D6: 前端页面布局

**决策**：`/notion` 页面分两区：

```
┌─────────────────────────────────────────────┐
│  < 2026年3月 >                              │
│  热力图（7列 × 5行，周一起始）              │
│  Mo Tu We Th Fr Sa Su                      │
│  ░  █  ░  ██ █  ░  █                       │
└─────────────────────────────────────────────┘
┌─────────────────────────────────────────────┐
│  3月16日                                    │
│  ┌── 今日记录 ──────────────────────────┐   │
│  │  Markdown 渲染                      │   │
│  └──────────────────────────────────────┘   │
│  ┌── AI 日报 ▼（可折叠）───────────────┐   │
│  │  Description 内容                   │   │
│  └──────────────────────────────────────┘   │
└─────────────────────────────────────────────┘
```

移动端热力图和详情上下堆叠，PC 端可考虑左右布局。

## Risks / Trade-offs

**[风险] 月份查询需要遍历所有页面** → 缓解：每月最多 31 条，量小可接受；queryDatabase 单次最多 50 条，一般一次够。

**[风险] block-count 并行 N 个请求可能触发 Notion API 限流（3 req/s）** → 缓解：前端控制并发数，最多 5 个并行请求；月份切换有防抖。

**[风险] image block 的 CDN URL 有过期时间** → 接受：页面打开时获取，短时间内有效；不做缓存。
