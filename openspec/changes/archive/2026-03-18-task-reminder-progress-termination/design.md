## Context

第一阶段建立了任务检测→追问→写入 Notion 的完整流程，并在 `HandlerWxPortalService` 中实现了消息路由骨架。`TaskDetectionResult.relatedTaskId` 已在检测阶段填充，但此前仅记录日志未使用。本阶段在此基础上补全任务生命周期：进度更新、终结、动态提醒、巡检提醒。

## Goals / Non-Goals

**Goals:**
- 用户消息关联 active 任务时自动更新进度
- 用户表达终结意图时更新任务状态（completed/abandoned/deleted）
- 任务创建后 AI 生成 cron 注册 Quartz Job，到时推送模板消息
- 每天 10:00/22:00 巡检，AI 判断哪些任务需要提醒

**Non-Goals:**
- Quartz Job 持久化到数据库（用 RAMJobStore，重启重建）
- 模板消息审批流程（需用户自行在微信公众平台申请）
- 多模板支持（本阶段只用一个通用任务提醒模板）

## Decisions

### 决策1：Quartz RAMJobStore + 启动重建

**选择**：使用 `RAMJobStore`，不引入 Quartz 专用表。应用启动时通过 `ApplicationRunner` 扫描所有用户的 active 任务，重新注册 Quartz Job。

**理由**：避免引入 10+ 张 Quartz 系统表；任务数量有限（每用户数个），启动重建开销可接受。

**重建策略**：
```
ApplicationStartupRunner
  └→ 遍历所有 ACTIVE 状态 user_config
       └→ getActiveTaskSummary(user)
            └→ 对每个 active 任务：
                 检查 task page 是否有 cron 属性
                 → 有：注册 Quartz Job
                 → 无：跳过（任务创建时 cron 已注册，此处作容错）
```

Tasks Database 需新增 `CronExpr` 属性存储 cron 表达式，供重建时读取。

---

### 决策2：AI 生成 cron，校验后兜底

**选择**：任务创建完成后，额外调用 AI 生成 cron 表达式（在 `finalizeDraft` 之后）。用 Quartz `CronExpression.isValidExpression()` 校验，非法则使用默认 `0 0 20 * * ?`（每天 20:00）。

**AI Prompt 要点**：
- 输入：任务名、类型、周期描述（如"每天"、"每周一"）
- 输出：标准 6 位 Quartz cron 表达式，仅返回 JSON `{"cron": "..."}`

---

### 决策3：进度更新与终结的意图识别

消息路由中，当 `relatedTaskId != null` 时进入进度更新分支。终结意图识别到后直接执行，无需用户确认——多余的确认步骤损害体验，用户说完了就是完了。

```
用户消息
  │
  ├─ relatedTaskId != null ──▶ 进度更新分支
  │     └→ appendConversationHistory
  │     └→ updateProgress(taskPageId, content)
  │     └→ 回复 "✅ 进度已记录：[任务名]"
  │
  ├─ 终结意图检测（新增一次 AI 调用）
  │     └→ 返回 {is_termination: true, status: "completed"|"abandoned"|"deleted", task_name: "..."}
  │     └→ 直接 updateTaskStatus → append 终结记录 → cancelReminder
  │     └→ 回复 "✅ 任务「XXX」已完成！" / "好的，任务已放弃。" / "已删除。"
  │
  └─ 其他 → 普通笔记 / 新任务（现有逻辑）
```

终结意图检测仅在 `relatedTaskId == null` 且 `isTask == false` 时触发（避免多余调用）。

---

### 决策4：模板消息方法与 45015 处理

**选择**：`WechatService.pushTemplateMessage(openId, templateId, data)` 使用微信模板消息 API（`/cgi-bin/message/template/send`），失败时只记录日志，不抛异常。

**模板变量（单模板）**：
```
{{task_name.DATA}}   任务名称
{{remind_content.DATA}}  提醒内容
{{progress.DATA}}    当前进度
```

`WX_TASK_TEMPLATE_ID` 作为环境变量，未配置时跳过推送（降级为日志）。

---

### 决策5：巡检逻辑

```
@Scheduled(cron = "0 0 10,22 * * ?")
TaskPatrolService.patrol()
  └→ 遍历所有 active user_config
       └→ getActiveTaskSummary(user)  // 含 last_updated 信息
            └→ AI 判断：哪些任务需要提醒？
                 Input: 任务列表（名称、类型、周期、最后更新时间）
                 Output: [{task_id, remind_message}] 或 []
            └→ 对需提醒的任务：pushTemplateMessage
```

巡检不依赖 Quartz，复用 Spring `@Scheduled`。

---

### 决策6：TaskNotionService 新增方法

- `updateTaskStatus(userConfig, pageId, status)` — 更新 Status select 属性
- `updateTaskProgress(userConfig, pageId, progress)` — 更新 Progress rich_text 属性
- `getCronExpr(userConfig, pageId)` — 读取 CronExpr 属性（启动重建用）
- `writeCronExpr(userConfig, pageId, cron)` — 写入 CronExpr 属性

Tasks Database 新增字段 `CronExpr`（rich_text）。

## Risks / Trade-offs

| 风险 | 说明 | 缓解 |
|------|------|------|
| 模板 ID 未配置 | 用户未在微信平台申请模板 | 未配置时降级为日志，不崩溃 |
| 重建时 Notion API 请求过多 | 用户多、任务多时启动慢 | 启动重建异步执行，不阻塞 HTTP 服务 |
| 终结误判 | AI 将普通消息判断为终结意图 | Prompt 设计保守，要求 AI 匹配任务名置信度高才返回终结意图；误判时用户可重新记录任务 |
| Quartz Job 重复注册 | 重启时重建 Job 与已有 Job 冲突 | 注册前先 `scheduler.deleteJob()`，再重新创建 |

## Migration Plan

1. `pom.xml` 添加 `spring-boot-starter-quartz` 依赖
2. `application.yml` 添加 Quartz RAMJobStore 配置
3. Tasks Database 新增 `CronExpr` 字段（对已有任务无影响，字段为空则跳过提醒）
4. 环境变量新增 `WX_TASK_TEMPLATE_ID`（可选，未设置时提醒功能静默降级）
5. 无数据迁移，进度更新/终结/提醒对已有任务自动生效

## Open Questions

- 终结确认步骤是否需要状态机支持？（需在 conversation_state 或 task_draft 中临时存储"待确认终结"状态）
- 巡检 AI 调用量：若用户多任务多，每次巡检 API 成本较高，是否需要限流或批量？
