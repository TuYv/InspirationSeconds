项目概述

- 技术栈：Spring Boot 2.7、Maven、WxJava、OkHttp、Jackson、MyBatis-Plus、H2/MySQL、Lombok、Commons Lang
- 功能：微信服务号交互、Notion配置对话、Tag提取与同步、AES加密存储、配置CRUD

快速开始

- 安装 JDK 8+
- 配置环境变量：`WX_APP_ID`、`WX_APP_SECRET`、`WX_TOKEN`、`AES_KEY`、可选 `NOTION_VERSION`
- 本地运行：`mvn spring-boot:run`

微信服务号对接

- 使用 ngrok 或 frp 暴露本地 `http://localhost:8080/wx/portal`
- 建议使用 HTTPS 地址：`https://<ngrok-id>.ngrok.io/wx/portal`
- 在微信公众平台开发者中心配置：
  - URL：上述外网地址
  - Token：与 `WX_TOKEN` 相同（消息签名校验用）
  - EncodingAESKey：与 `WX_ENCODING_AES_KEY` 相同（兼容/安全模式）
  - 开发者密码 AppID/AppSecret：与环境变量一致
  - 消息加解密方式：明文或兼容模式

本地调试步骤

- 启动应用，确保 `/wx/portal` GET 返回微信校验的 `echostr`
- 在公众号发送消息：
  - `配置Notion`：进入配置流程
  - 粘贴 Notion API Key
  - 粘贴数据库ID（验证通过后保存配置）
  - 任意消息触发同步，含或不含 `#标签`
  - `查询我的配置`：查看当前配置

数据库配置

- 默认使用内存 H2（自动执行 `schema.sql` 初始化表）；生产使用 `application-prod.yml` 配置 MySQL，并以 `--spring.profiles.active=prod` 启动（按需执行 DDL）

部署与运维

- 详细部署步骤与命令行示例见 `docs/deploy.md`

安全

- 使用 `AES/CBC/PKCS5Padding` 加密存储 Notion API Key，密钥来自 `AES_KEY`（16/32字节hex）

Notion API 使用说明

- 验证：`GET /v1/databases/{databaseId}`，需要 `Authorization: Bearer <key>` 与 `Notion-Version`
- 同步：`POST /v1/pages`，动态查找数据库的 `title` 属性名作为页面标题；标签作为段落内容附加

功能测试用例见 `docs/test-cases.md`；常见异常与处理见 `docs/exceptions.md`。
