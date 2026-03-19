## Why

当前所有用户的 AI 调用共用系统全局配置（base_url / api_key / model），用户无法接入自己的模型，也无法了解自己的 token 消耗情况。随着任务管理、巡检提醒等功能上线，AI 调用量显著增加，用户有自带模型和追踪用量的实际需求。

## What Changes

- `user_config` 表新增三列：`ai_base_url`、`ai_api_key`（AES 加密）、`ai_model`，存储用户自定义 AI 配置
- `AiService` 新增 `chat(UserConfig, prompt, msg)` 重载：优先使用用户配置，未配置则 fallback 系统全局配置
- 新建 `token_usage` 表，按（用户 + 日期）聚合 token 用量；仅在用户使用自己的 API Key 时写入
- 设置页新增"自定义 AI 模型"配置区块（base_url / api_key 脱敏展示 / model）
- 设置页新增"查看 Token 用量"入口，跳转独立用量页面展示日明细
- 有 UserConfig 上下文的 AI 调用点（任务检测、巡检、生命周期等）改用新重载；DailySummaryService / WeeklySummaryService 保持走系统配置

## Capabilities

### New Capabilities

- `user-ai-config`: 用户自定义 AI 模型配置（base_url / api_key / model），加密存储，前端脱敏展示
- `token-usage-tracking`: 按天聚合用户 AI token 用量，仅统计用户自有 key 的消耗，前端独立页面展示

### Modified Capabilities

- `message-sync`: `AiService` 调用点需感知用户配置，相关服务签名变更（传入 UserConfig）

## Impact

- **DB schema**: `user_config` 新增 3 列；新建 `token_usage` 表
- **AiService**: 新增重载方法，解析响应中的 `usage` 字段
- **涉及服务**: `TaskDetectionService`、`TaskDraftService`、`TaskLifecycleService`、`TaskPatrolService`、`TaskReminderService`
- **新增 API**: `GET /api/user/token-usage`、`PATCH /api/user/ai-config`
- **前端**: `SettingsView` 新增两个区块，新增 `TokenUsageView` 路由页面
- **依赖**: 无新增第三方依赖
