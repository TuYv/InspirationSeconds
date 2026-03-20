## MODIFIED Requirements

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

## REMOVED Requirements

### Requirement: Quartz Job 到时推送模板消息
**Reason**: 微信平台拒绝了"每日进度触发通知"模板消息，理由是不支持非用户主动触发场景。
**Migration**: 由上方"Quartz Job 到时推送客服消息"需求替代，使用 `pushMessageToUser` 接口。
