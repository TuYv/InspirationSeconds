我已制定了增强 Notion 功能的详细计划，主要包括代码重构和功能增强两部分。

## 计划内容

### 1. 基础重构：引入 DTO 与 JSON 序列化
目前的代码使用字符串拼接生成 JSON，难以维护且容易出错。我将引入 Java 对象模型 (DTO) 来映射 Notion API 的请求结构。
*   **创建 DTO 模型**：在 `com.example.wxnotion.model.notion` 包下定义 `NotionPageRequest`, `Parent`, `Block`, `RichText` 等类。
*   **优势**：支持复杂的嵌套结构（如列表、待办事项），提高代码可读性和稳定性。

### 2. 功能增强：智能分段与 Markdown 支持
改善微信输入到 Notion 的转换体验。
*   **智能标题分离**：
    *   将消息的 **第一行** 自动识别为 **页面标题**。
    *   **剩余内容** 自动识别为 **页面正文**。
*   **Markdown 语法识别**：
    *   `- ` 或 `* ` 开头 → **无序列表 (Bulleted List)**
    *   `[] ` 开头 → **待办事项 (To-do List)**
    *   其他内容 → **普通段落 (Paragraph)**
*   **TagUtil 优化**：修改标签解析逻辑，保留原文的换行符，确保多行笔记格式不乱。

### 3. 服务层逻辑更新
*   **SyncService**：负责将用户消息切分为标题和正文。
*   **NotionService**：负责将正文转换为对应的 Notion Block 对象列表。

## 执行步骤
1.  **修改 TagUtil**：移除破坏换行的正则替换。
2.  **创建 DTOs**：定义 Notion API 所需的 Java 类。
3.  **重构 NotionService**：使用 DTO 替代字符串拼接，实现 Block 转换逻辑。
4.  **更新 SyncService**：接入新的分段逻辑。

准备好后，请确认开始执行。