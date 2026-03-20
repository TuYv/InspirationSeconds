## MODIFIED Requirements

### Requirement: 每天两次巡检所有 active 任务
系统 SHALL 在每天 10:00 和 22:00（Asia/Shanghai）扫描所有 active 用户的 active 任务，调用 AI 判断哪些任务需要提醒，并推送客服消息。

#### Scenario: 巡检发现长期未更新任务并提醒
- **WHEN** 巡检时某用户有一个周期为"每天"的任务，且对话历史最后更新超过 AI 判断的阈值
- **THEN** AI 判断需要提醒，系统使用 AI 生成的完整提醒文本推送客服消息

#### Scenario: 巡检判断无需提醒时跳过
- **WHEN** 巡检时某任务刚刚更新过进度
- **THEN** AI 判断不需要提醒，系统跳过，不推送消息

#### Scenario: AI 生成的提醒文本包含任务上下文
- **WHEN** AI 输出某任务的提醒项时
- **THEN** `remind_message` 字段包含完整的客服消息文本（含任务名、进度上下文和询问语），系统直接将其作为消息内容推送，不再添加固定前缀或格式包装
