关键场景

- 配置Notion：发送“配置Notion”后进入流程，回复引导话术
- 提交API Key：收到Key后引导提交数据库ID
- 验证配置：提交数据库ID后调用Notion验证成功/失败分支
- 发送带Tag消息同步：例如“今天加班 #工作 #日报”，提取正文与标签并创建页面
- 修改配置：发送“修改Notion配置”重新进入流程并覆盖原配置
- 查询配置：发送“查询我的配置”返回当前状态

接口级测试

- `/wx/portal` GET：签名正确返回`echostr`，签名错误返回空
- `/wx/portal` POST：文本消息进入流程与同步路径，回复XML被动消息
- `/api/configs`：增删改查配置记录

单元测试建议

- Tag解析：`TagUtilTest` 覆盖有/无标签两种情况
- Notion创建：使用 MockWebServer 或替身方法验证创建流程

