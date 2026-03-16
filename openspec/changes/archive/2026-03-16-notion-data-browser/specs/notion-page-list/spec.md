## ADDED Requirements

### Requirement: 按月查询页面列表
系统 SHALL 提供 `GET /api/notion/pages?year=YYYY&month=MM` 接口，从 JWT 提取 openId，查询 user_config 获取并解密 Notion token，调用 NotionService.queryDatabase() 获取全部页面，过滤出 title 以 `YYYY-MM-` 开头的页面，返回 `[{date, pageId}]` 数组（按 date 升序）。接口需要 JWT 鉴权。

#### Scenario: 查询有内容的月份
- **WHEN** 已登录用户请求 `GET /api/notion/pages?year=2026&month=03`，且该月有 10 个页面
- **THEN** 返回 HTTP 200，body 为 10 条 `{date: "2026-03-16", pageId: "xxx"}` 记录，按 date 升序

#### Scenario: 查询无内容的月份
- **WHEN** 已登录用户请求某个无页面的月份
- **THEN** 返回 HTTP 200，body 为空数组 `[]`

#### Scenario: 未登录请求返回 401
- **WHEN** 请求未携带有效 JWT
- **THEN** 返回 HTTP 401

---

### Requirement: 查询页面 block 数量
系统 SHALL 提供 `GET /api/notion/pages/{pageId}/block-count` 接口，调用 `retrieveBlockChildren` 获取 block 列表，返回 `{blockCount: N}`。接口需要 JWT 鉴权。

#### Scenario: 正常返回 block 数
- **WHEN** 已登录用户请求有效 pageId 的 block-count
- **THEN** 返回 HTTP 200，body 为 `{blockCount: 12}`

#### Scenario: 页面不存在或无权限
- **WHEN** pageId 不存在或 Notion 返回 403
- **THEN** 返回 HTTP 200，body 为 `{blockCount: 0}`（不抛错，降级处理）
