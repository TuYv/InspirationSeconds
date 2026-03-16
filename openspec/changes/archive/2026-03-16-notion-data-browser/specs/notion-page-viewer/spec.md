## ADDED Requirements

### Requirement: 读取页面内容并转为 Markdown
系统 SHALL 提供 `GET /api/notion/pages/{pageId}/content` 接口，调用 `retrieveBlockChildren` 获取 blocks，通过扩展后的 `BlockContentParser.toMarkdown()` 转换为 Markdown 字符串；同时调用 `getPageProperty(apiKey, pageId, "Description")` 获取 AI 日报文本。返回 `{markdown, aiSummary}`。接口需要 JWT 鉴权。

#### Scenario: 正常返回内容
- **WHEN** 已登录用户请求有内容的页面
- **THEN** 返回 HTTP 200，body 包含 `markdown`（非空字符串）和 `aiSummary`（可为空字符串）

#### Scenario: 有内容无日报
- **WHEN** 页面有正文但 Description 属性为空
- **THEN** 返回 HTTP 200，`markdown` 非空，`aiSummary` 为空字符串 `""`

#### Scenario: 页面不存在
- **WHEN** pageId 无效或无权访问
- **THEN** 返回 HTTP 404

---

### Requirement: BlockContentParser 支持 Markdown 输出
`BlockContentParser` SHALL 新增 `static String toMarkdown(JsonNode blocks)` 方法，支持以下 block 类型转换：
- `paragraph` → 普通段落
- `heading_1/2/3` → `#` / `##` / `###`
- `bulleted_list_item` → `- `
- `numbered_list_item` → `1. `
- `to_do` → `- [ ] ` 或 `- [x] `
- `quote` → `> `
- `code` → ` ``` ``` `
- `callout` → `> ` + emoji 前缀
- `divider` → `---`
- `image` → `![](url)`
- 其他未知类型 → 提取 plain_text 降级处理

#### Scenario: 段落和标题转换
- **WHEN** blocks 包含 paragraph 和 heading_2
- **THEN** 返回对应 Markdown，heading_2 转为 `## 标题内容`

#### Scenario: 未知 block 类型降级
- **WHEN** blocks 包含系统不认识的 block 类型
- **THEN** 尝试提取 plain_text，失败则跳过，不抛异常
