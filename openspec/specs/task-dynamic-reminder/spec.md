## Requirements

### Requirement: 任务创建后 AI 生成 cron 并注册 Quartz Job
任务写入 Notion 后，系统 SHALL 调用 AI 为该任务生成一个 Quartz cron 表达式，校验合法性后注册 Quartz Job，并将 cron 写入 Notion 任务页 CronExpr 属性。

#### Scenario: 周期任务生成合法 cron
- **WHEN** 任务类型为 recurring，周期描述为 "每天"
- **THEN** AI 返回如 `0 0 20 * * ?`，校验通过，注册 Quartz Job，cron 写入 Notion

#### Scenario: AI 返回非法 cron 时使用默认值
- **WHEN** AI 返回的 cron 表达式无法通过 `CronExpression.isValidExpression()` 校验
- **THEN** 系统使用默认 cron `0 0 20 * * ?`（每天 20:00），记录警告日志

#### Scenario: 一次性任务也生成提醒
- **WHEN** 任务类型为 one_time
- **THEN** AI 根据任务内容（截止时间/进度）决定提醒频率，如 "每周一次" → `0 0 10 ? * MON`

---

### Requirement: Quartz Job 到时推送客服消息
Quartz Job 触发时，系统 SHALL 调用 AI 根据任务名称和当前进度生成一段友好的询问文案，然后通过微信客服消息接口向用户推送。推送失败时记录日志，不影响下次 Job 调度。

#### Scenario: 正常生成文案并推送客服消息
- **WHEN** Quartz Job 触发，用户记录存在且 AI 返回有效文案
- **THEN** 系统将 AI 生成的完整消息文本通过客服消息接口发送给用户

#### Scenario: AI 调用失败时使用 fallback 文案
- **WHEN** Quartz Job 触发，但 AI 调用抛出异常或返回空内容
- **THEN** 系统使用固定 fallback 文案（如 `"[任务名] 该更新进度了~"`）推送客服消息，记录 WARN 日志

#### Scenario: 无需配置模板 ID
- **WHEN** 环境变量 WX_TASK_TEMPLATE_ID 未配置
- **THEN** 系统正常推送，不依赖该变量，不打印降级日志

---

### Requirement: 应用启动时从 Notion 重建 Quartz Jobs
系统 SHALL 在启动时通过 `ApplicationRunner` 异步扫描所有 active 任务，读取 CronExpr 属性，重新注册 Quartz Job，实现重启容错。

#### Scenario: 启动重建已有任务的 Job
- **WHEN** 应用重启，Notion 中存在 CronExpr 非空的 active 任务
- **THEN** ApplicationRunner 异步读取并注册对应 Quartz Job，不阻塞 HTTP 服务启动

#### Scenario: CronExpr 为空的任务跳过
- **WHEN** 任务页 CronExpr 属性为空（如旧任务创建时未生成 cron）
- **THEN** 跳过该任务，不注册 Job，记录 DEBUG 日志
