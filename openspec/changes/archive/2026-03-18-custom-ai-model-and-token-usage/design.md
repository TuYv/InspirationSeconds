## Context

当前 `AiService` 是无状态单例，通过 `@Value` 注入全局 `baseUrl`/`apiKey`/`model`，所有用户共用。`UserConfig` 已有 AES 加密存储 Notion token 的先例，可直接复用加密工具。OpenAI 兼容接口的响应体中包含 `usage` 字段（`prompt_tokens`/`completion_tokens`/`total_tokens`），当前代码丢弃不用。

## Goals / Non-Goals

**Goals:**
- 用户可在设置页配置自己的 AI base_url / api_key / model
- 用户配置优先，未配置 fallback 系统全局配置（DailySummary / WeeklySummary 保持系统配置）
- 仅在用户使用自己的 API Key 时记录 token 用量，按天聚合
- 前端设置页脱敏展示 API Key，用量独立页面展示日明细

**Non-Goals:**
- 不统计走系统 API Key 的 token 用量
- 不支持 per-request 粒度的用量查询
- 不做模型列表的 API 探测（用户自填 model 名称）
- DailySummaryService / WeeklySummaryService 不改动

## Decisions

### 1. 新增显式列而非放入 promptConfig JSON

`user_config` 新增 `ai_base_url VARCHAR(512)`、`ai_api_key VARCHAR(2048)`（AES 加密）、`ai_model VARCHAR(256)` 三列。

**为什么不放 promptConfig JSON？** API Key 需要和 Notion token 一样做 AES 加密存储；JSON 列中的加密字段处理复杂，且无法利用 MyBatis-Plus 的列级类型处理器。

### 2. AiService 新增重载，不破坏现有调用

```
chat(String systemPrompt, String userMessage)              // 现有，系统配置
chat(UserConfig user, String systemPrompt, String userMessage)  // 新增
```

新重载内部判断：`user.aiApiKey != null` 则用用户配置并记录 usage；否则委托原方法。

**为什么不改成统一签名？** 有 6 处调用点，其中 DailySummaryService / WeeklySummaryService 无 UserConfig 上下文且应走系统配置；保留原方法可零改动这两处。

### 3. token_usage 按天原子聚合

```sql
CREATE TABLE token_usage (
  id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
  open_id            VARCHAR(64)  NOT NULL,
  usage_date         DATE         NOT NULL,
  prompt_tokens      INT          NOT NULL DEFAULT 0,
  completion_tokens  INT          NOT NULL DEFAULT 0,
  total_tokens       INT          NOT NULL DEFAULT 0,
  call_count         INT          NOT NULL DEFAULT 0,
  UNIQUE KEY uk_open_id_date (open_id, usage_date)
);
```

写入使用 `INSERT ... ON DUPLICATE KEY UPDATE total_tokens = total_tokens + VALUES(total_tokens), ...`，H2 和 MySQL 均兼容，无并发竞争问题。

**为什么不按 model 分组？** 用户说"只需要记录 token 使用量"，按天够用。model 切换时同一天合并，逻辑更简单。

### 4. TaskDraftService 改由调用方传入 UserConfig

`TaskDraftService.matchReplyToDraft` / `generateClarificationQuestion` 目前有 AI 调用，接收 `openId`。改为接收 `UserConfig`（调用方 HandlerWxPortalService 已持有）。避免在 TaskDraftService 内部额外查 DB。

### 5. API Key 脱敏策略

后端返回时：`sk-****...` + 后4位（若 key 长度 < 8 则全掩码）。前端永远得不到明文 key。编辑时若用户提交空字符串 = 清除配置（回到系统 fallback）。

## Risks / Trade-offs

- **用户配置错误的 URL/Key** → AiService 调用失败时已有降级日志返回，行为与现在一致，不会崩溃
- **ON DUPLICATE KEY 在 H2 的兼容性** → H2 2.x 支持此语法；已在现有 schema.sql 中验证过 H2 兼容写法
- **TaskDraftService 签名变更** → 调用方只有 HandlerWxPortalService，影响范围可控；测试需同步更新

## Migration Plan

1. `schema.sql` 新增列和新表（`IF NOT EXISTS` + 列默认 NULL，存量用户无影响）
2. 部署后存量用户 `ai_api_key = NULL`，自动 fallback 系统配置，行为不变
3. 无需数据迁移脚本，无 rollback 风险

## Open Questions

无。
