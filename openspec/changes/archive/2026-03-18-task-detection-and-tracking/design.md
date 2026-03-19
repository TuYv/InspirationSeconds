## Context

InspirationSeconds 是微信公众号 + Notion 的笔记同步系统。消息处理链完全异步：`HandlerWxPortalService` 接收消息后通过 `@Async` 调用 `SyncService.sync()`，结果通过客服消息 API 回复用户。当前 `SyncService` 直接将所有文字消息写入 Notion，无任何意图识别。

本设计在 `SyncService.sync()` 调用前插入任务检测分支，复用现有 `AiService` 进行 AI 调用，复用 `NotionApiFacade` 进行 Notion 操作。

## Goals / Non-Goals

**Goals:**
- 每条文字消息经 AI 判断是否为任务
- 任务分类（周期/一次性）并提取结构化字段
- 多轮追问补全缺失字段，支持用户同时有多个待确认草稿
- 任务写入用户专属的独立 Notion Tasks Database
- 每个任务页面追加对话历史（供复盘和后续 AI 整理）

**Non-Goals:**
- 定时提醒（Quartz + 模板消息）
- 巡检提醒（每天 10:00/22:00）
- 进度更新和任务终结流程
- 现有笔记同步逻辑的任何变更

## Decisions

### 决策1：消息处理入口放在 HandlerWxPortalService，而非 SyncService 内部

**选择**：在 `HandlerWxPortalService.processMessageAsync()` 的 `text` 分支中，先调用 `TaskDetectionService`，再决定是否调用 `SyncService.sync()`。

**理由**：`SyncService` 职责单一（笔记同步），不应感知任务逻辑。`HandlerWxPortalService` 已是消息路由层，在此做分发更符合现有架构边界。

**备选**：在 `SyncService` 内部前置检测 → 耦合过重，违反单一职责。

---

### 决策2：task_draft 表独立存储多任务草稿

**选择**：新建 `task_draft` 表，`open_id` 非唯一（一个用户可有多个草稿），含 `draft_json`（已收集字段 + 待收集字段列表）和 `conversation_log`（JSON 数组）。

**理由**：用户可能连续发两条任务消息，两个草稿同时追问。复用 `conversation_state` 表（单行唯一）无法满足。

**草稿状态流**：
```
创建草稿(PENDING) → 追问补全 → 写入Notion → 删除草稿
```

**追问优先级**：若用户有多个 PENDING 草稿，AI 判断当前回复对应哪个草稿（根据语义匹配）。

---

### 决策3：Notion Tasks Database 按需创建，ID 存入 user_config

**选择**：首次创建任务时检查 `user_config.tasks_database_id`，若为空则调用 Notion API 在用户的 root page 下创建 Tasks Database，并将 ID 回写。

**理由**：避免每次任务操作都去搜索或推断 DB 位置；与现有 `database_id`（Notes DB）的管理方式一致。

**Tasks Database 字段**：
```
Name (title) | Type | Cycle | Trigger | Progress | EndCondition | Status | CreatedAt
```

---

### 决策4：对话历史以 Notion blocks 追加到任务页面

**选择**：每轮对话（用户消息 + 系统回复）在任务创建后追加为 Notion page blocks（callout 或 paragraph 格式，含时间戳和角色标识）。

**草稿阶段**：对话暂存在 `task_draft.conversation_log`（JSON）。任务创建时，将草稿中的历史对话一并写入 Notion task page，然后删除草稿。

**理由**：对话历史在 Notion 中自然可读，后续 AI 整理只需读 page blocks，无需额外存储。

---

### 决策5：AI 调用策略

**第一次调用（检测+提取）**：
- Input：用户消息 + 用户现有 active 任务列表摘要（从 Notion 拉取，后期可缓存）
- Output JSON：
```json
{
  "is_task": true,
  "task_type": "recurring | one_time",
  "extracted": {
    "name": "...",
    "cycle": "...",
    "trigger": "...",
    "current_progress": "...",
    "end_condition": "..."
  },
  "missing_fields": ["current_progress", "end_condition"]
}
```

**第二次调用（生成追问话术，仅当 missing_fields 非空）**：
- Input：任务草稿 + 待收集字段
- Output：自然语言追问文本

**草稿回复匹配调用（有 PENDING 草稿时）**：
- Input：用户消息 + 所有 PENDING 草稿摘要
- Output：匹配到的 draft_id（或 null 表示是新任务/普通笔记）

---

### 决策6：任务状态为字符串枚举，软删除

状态值：`active` / `completed` / `abandoned` / `deleted`

`deleted` 仅更新 Notion task page 的 Status 属性，数据保留，不物理删除。

## Risks / Trade-offs

| 风险 | 说明 | 缓解 |
|------|------|------|
| AI 误判 | 普通笔记被识别为任务 | Prompt 设计保守，宁可漏报不误报；用户收到确认消息可纠正 |
| Notion API 延迟 | 每条消息多一次拉取 active 任务列表 | 处理链已异步，延迟可接受；后期可本地缓存任务列表 |
| 草稿堆积 | 用户不回答追问导致 PENDING 草稿长期堆积 | 设置草稿超时（7天），过期自动标记放弃 |
| tasks_database_id 丢失 | 数据库重置后 user_config 数据丢失 | 检测时若 ID 失效则重新创建 DB |
| Notion Tasks DB 创建失败 | 访客用户 root page 权限不足 | 访客模式下使用 admin token，在 guest root page 下创建 |

## Migration Plan

1. 执行 schema 变更：`task_draft` 表，`user_config.tasks_database_id` 列
2. 部署新代码（功能默认开启，消息流仍兼容旧路径）
3. 无需数据迁移，Tasks Database 按用户首次任务创建时懒初始化
4. 回滚：注释 `HandlerWxPortalService` 中的 `TaskDetectionService` 调用即可

## Open Questions

- 访客用户是否支持任务功能？（访客共享 admin token，Tasks DB 应建在哪个 root page 下？）
- 后续进度更新时，AI 需要匹配现有任务——是否需要在 DB 层缓存任务列表以减少 Notion API 调用？
