## 1. DB Schema

- [x] 1.1 `schema.sql` — `user_config` 新增 `ai_base_url VARCHAR(512)`、`ai_api_key VARCHAR(2048)`、`ai_model VARCHAR(256)` 三列（DEFAULT NULL）
- [x] 1.2 `schema.sql` — 新建 `token_usage` 表（open_id, usage_date, prompt_tokens, completion_tokens, total_tokens, call_count，唯一键 uk_open_id_date）
- [x] 1.3 `UserConfig` 实体新增对应字段
- [x] 1.4 新建 `TokenUsage` 实体类及 `TokenUsageMapper`

## 2. AiService 改造

- [x] 2.1 `AiService.chat(UserConfig, prompt, msg)` 新增重载：若 user.aiApiKey 非空则用用户配置，否则委托原 `chat(prompt, msg)`
- [x] 2.2 新重载解析响应 `usage` 字段（prompt_tokens / completion_tokens / total_tokens）
- [x] 2.3 新重载在用户有自有 Key 且 usage 解析成功时，调用 `TokenUsageService.record(openId, usage)`
- [x] 2.4 新建 `TokenUsageService.record(openId, usage)` — 执行 `INSERT ... ON DUPLICATE KEY UPDATE` 原子累加当日用量

## 3. 后端 API

- [x] 3.1 `UserController` 新增 `PATCH /api/user/ai-config` 接口，接受 aiBaseUrl / aiApiKey / aiModel，aiApiKey 做 AES 加密存储（空字符串 = 清除）
- [x] 3.2 `UserConfigController.UserConfigView` / `UserController.me()` 返回值中新增 aiBaseUrl、aiApiKeyMasked（脱敏）、aiModel 字段
- [x] 3.3 新建 `TokenUsageController`，`GET /api/user/token-usage` 返回当前用户所有日期用量，按 usage_date 倒序

## 4. 调用点切换

- [x] 4.1 `TaskDetectionService.detectTask(UserConfig, message, summary)` — 签名新增 UserConfig，内部改用 `aiService.chat(userConfig, ...)`
- [x] 4.2 `TaskDraftService.matchReplyToDraft / generateClarificationQuestion` — 签名新增 UserConfig，内部改用新重载
- [x] 4.3 `TaskLifecycleService.detectTerminationIntent(UserConfig, message, summary)` — 签名新增 UserConfig，内部改用新重载
- [x] 4.4 `TaskReminderService.generateCronForTask(UserConfig, taskName, taskType, cycle)` — 签名新增 UserConfig，内部改用新重载
- [x] 4.5 `TaskPatrolService.patrolUser(UserConfig user)` — 内部 AI 调用改用 `aiService.chat(user, ...)`
- [x] 4.6 `HandlerWxPortalService` — 所有向上述服务的调用传入 UserConfig

## 5. 前端 — 设置页 AI 配置区块

- [x] 5.1 `SettingsView.vue` 新增"自定义 AI 模型"折叠区块（base_url 输入框、api_key password 输入框、model 输入框）
- [x] 5.2 加载时从 `/api/user/me` 读取并回显 aiBaseUrl、aiKeyMasked、aiModel；api_key 展示脱敏值，编辑时清空
- [x] 5.3 保存时调用 `PATCH /api/user/ai-config`，成功后重新加载脱敏展示
- [x] 5.4 `SettingsView.vue` 新增"查看 Token 用量 →"按钮，点击跳转 `/token-usage`

## 6. 前端 — Token 用量页面

- [x] 6.1 新建 `TokenUsageView.vue`，调用 `GET /api/user/token-usage` 展示日明细表格（日期 / 输入 tokens / 输出 tokens / 合计 / 调用次数）
- [x] 6.2 表格底部展示合计行（各列求和）
- [x] 6.3 空数据时展示友好提示（"暂无用量记录，使用自定义 AI Key 后将在此显示"）
- [x] 6.4 `router/index.ts`（或同等路由文件）新增 `/token-usage` 路由，指向 `TokenUsageView`

## 7. 测试

- [x] 7.1 单测 `AiService` 新重载：用户有 Key 时用用户配置、无 Key 时走系统配置、usage 解析写入
- [x] 7.2 单测 `TokenUsageService.record`：首次写入、同天累加、AI 失败不写入
- [x] 7.3 单测 `UserController PATCH /api/user/ai-config`：加密存储、空字符串清除、脱敏返回
- [x] 7.4 单测各服务签名变更：TaskDetectionService / TaskDraftService / TaskLifecycleService 新签名调用正确
