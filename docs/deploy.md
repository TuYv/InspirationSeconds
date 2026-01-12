# 部署与配置指南

本指南涵盖：环境准备、配置项说明、数据库初始化、Docker 镜像构建与运行、微信公众平台接入、Notion 配置对话与同步、运维命令与排错。所有步骤均附带命令行示例，便于直接复制执行。

## 1. 环境准备
- 安装 `docker` 与 `docker compose`
- 确保服务器可访问宿主机上的 MySQL(`3306`) 与 Redis(`6379`)（你已存在容器并映射宿主机端口）
- 拥有可被微信访问的公网域名与 HTTPS 站点，用于服务入口 `https://<域名>/wx/portal`

```bash
# Ubuntu 示例：安装 Docker 与 Compose
sudo apt-get update -y
sudo apt-get install -y ca-certificates curl gnupg lsb-release
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update -y
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# 验证安装
docker --version
docker compose version
```

## 2. 配置项说明
- 数据库（生产使用 `application-prod.yml` 与环境变量）
  - `DB_HOST`：默认 `host.docker.internal`（容器访问宿主机）。Linux 下已在 `docker-compose.yml` 添加 `extra_hosts: host-gateway`，如无效可替换为宿主机 IP
  - `DB_NAME`：默认 `wxnotion`
  - `DB_USERNAME`：默认 `root`
  - `DB_PASSWORD`：默认 `root`
- 微信（公众号）
  - `WX_APP_ID`：公众号 AppID
  - `WX_APP_SECRET`：公众号 AppSecret
  - `WX_TOKEN`：服务器配置里的 Token（签名校验用）
  - `WX_ENCODING_AES_KEY`：消息加解密密钥（43位，兼容/安全模式需要）
- 其他
  - `AES_KEY`：16 或 32 字节十六进制密钥（本项目用于 AES 加密存储敏感信息）
  - `NOTION_VERSION`：默认 `2022-06-28`
  - `SPRING_PROFILES_ACTIVE`：默认 `prod`

```bash
# 示例：导出环境变量（按需替换实际值）
export DB_HOST=host.docker.internal
export DB_NAME=wxnotion
export DB_USERNAME=root
export DB_PASSWORD=root
export WX_APP_ID=wx_your_app_id
export WX_APP_SECRET=your_app_secret
export WX_TOKEN=your_token
export WX_ENCODING_AES_KEY=your_encoding_aes_key_43chars
export AES_KEY=0123456789abcdef0123456789abcdef
export NOTION_VERSION=2022-06-28
```

## 3. 初始化数据库
生产环境不会自动执行 `schema.sql`，请在 MySQL 中手动创建表结构。

```sql
CREATE DATABASE IF NOT EXISTS wxnotion DEFAULT CHARACTER SET utf8mb4;
USE wxnotion;

CREATE TABLE IF NOT EXISTS user_config (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  open_id VARCHAR(64) NOT NULL UNIQUE,
  app_type VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  encrypted_api_key VARCHAR(2048) NOT NULL,
  database_id VARCHAR(128) NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS conversation_state (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  open_id VARCHAR(64) NOT NULL UNIQUE,
  step VARCHAR(32) NOT NULL,
  temp_api_key VARCHAR(4096),
  updated_at TIMESTAMP NOT NULL
);
```

```bash
# 使用宿主机 MySQL 客户端导入（按需替换密码与 IP）
mysql -h 127.0.0.1 -P 3306 -u root -p < /path/to/your/schema.sql
# 或直接登录并粘贴上述 SQL
mysql -h 127.0.0.1 -P 3306 -u root -p
```

## 4. Docker 构建与运行
项目已提供 `Dockerfile` 与 `docker-compose.yml`。

```bash
# 方式 A：使用 Compose（推荐）
cd /path/to/InspirationSeconds
# 构建镜像
docker compose build
# 以生产配置启动（端口映射 8080:8080）
docker compose up -d
# 查看日志
docker compose logs -f
# 重启服务
docker compose restart
```

```bash
# 方式 B：单容器运行（不使用 Compose）
cd /path/to/InspirationSeconds
# 构建镜像
docker build -t wx-notion:latest .
# 运行容器（按需替换环境变量）
docker run -d --name wx-notion -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_HOST=${DB_HOST} -e DB_NAME=${DB_NAME} \
  -e DB_USERNAME=${DB_USERNAME} -e DB_PASSWORD=${DB_PASSWORD} \
  -e WX_APP_ID=${WX_APP_ID} -e WX_APP_SECRET=${WX_APP_SECRET} \
  -e WX_TOKEN=${WX_TOKEN} -e WX_ENCODING_AES_KEY=${WX_ENCODING_AES_KEY} \
  -e AES_KEY=${AES_KEY} -e NOTION_VERSION=${NOTION_VERSION} \
  wx-notion:latest
# 查看容器日志
docker logs -f wx-notion
```

## 5. 反向代理（可选）
建议使用 Nginx 将 `https://<域名>/wx/portal` 反向代理到容器 `8080`。

```bash
# 安装 Nginx（Ubuntu 示例）
sudo apt-get install -y nginx
# 创建站点配置
sudo tee /etc/nginx/sites-available/wx-notion.conf >/dev/null <<'EOF'
server {
    listen 80;
    server_name your.domain.com;

    location /wx/portal {
        proxy_pass http://127.0.0.1:8080/wx/portal;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
EOF
# 启用并重载
sudo ln -sf /etc/nginx/sites-available/wx-notion.conf /etc/nginx/sites-enabled/wx-notion.conf
sudo nginx -t && sudo systemctl reload nginx
```

> HTTPS 建议使用 certbot 或云厂商证书服务；完成后将 80 重定向到 443。

## 6. 微信公众平台接入
- 登录 mp.weixin.qq.com → 设置与开发/开发管理 → 服务器配置/消息接口配置
- 填写：
  - `URL`：`https://your.domain.com/wx/portal`
  - `Token`：与环境变量 `WX_TOKEN` 一致
  - `EncodingAESKey`：与 `WX_ENCODING_AES_KEY` 一致（选择兼容或安全模式）
- 提交后微信会发起 `GET` 验证，服务端成功返回 `echostr` 即接入成功（签名校验由代码自动完成）。

## 7. Notion 交互式配置与同步
- 在微信里向你的服务号发送：`配置Notion`
- 按提示先粘贴 Notion API Key，再粘贴数据库 ID（可从数据库页面 URL 拿到）
- 验证成功后，发送任意文本：例如 `今天加班 #工作 #日报`，系统会提取正文与标签并创建 Notion 页面

## 8. 本地开发与验证（可选）
```bash
# 本地运行（H2 内存库）
mvn -q -DskipTests package
mvn spring-boot:run
# 暴露到公网用于微信回调（ngrok 示例）
brew install ngrok # 或按官方文档安装
ngrok http 8080
# 将 ngrok 生成的 HTTPS 地址配置到微信后台 URL，如 https://<id>.ngrok.io/wx/portal
```

## 9. 运维命令
```bash
# 查看 Compose 状态
docker compose ps
# 查看所有日志（包含历史）
docker compose logs --tail=200 -f
# 重启服务
docker compose restart
# 停止服务
docker compose down
# 更新镜像（重新构建, 无缓存）
docker compose build --no-cache && docker compose up -d
# 单容器日志
docker logs -f wx-notion
# 进入容器交互
docker exec -it wx-notion /bin/sh
```

## 10. 常见排错
- 微信验证失败：
  - `URL` 必须为公网 HTTPS；`Token` 与服务端一致；加密模式下 `EncodingAESKey` 正确
- 数据库连通性：
  - 将 `DB_HOST` 改为宿主机实际 IP；或确认 `host.docker.internal` 映射通过 `extra_hosts`
- Notion 验证失败：
  - API Key 权限有效；数据库 ID 正确；数据库存在 title 属性
- 端口未开放：
  - 安全组/防火墙放行 `80/443/8080` 或反向代理端口

---
如需接入 Redis（缓存/限流）、加入健康检查与监控、提供 CI/CD 构建与推送镜像配置，可以在此文档基础上扩展；欢迎继续提出需求。
