#!/bin/bash

# 打印带颜色的日志
log() {
    echo -e "\033[32m[Deploy] $1\033[0m"
}

error() {
    echo -e "\033[31m[Error] $1\033[0m"
}

# 1. 拉取最新代码
log "Pulling latest code..."
git pull
if [ $? -ne 0 ]; then
    error "Git pull failed!"
    exit 1
fi

# 2. 构建镜像
log "Building Docker image..."
docker compose build
if [ $? -ne 0 ]; then
    error "Docker build failed!"
    exit 1
fi

# 3. 重启服务
log "Restarting services..."
docker compose up -d
if [ $? -ne 0 ]; then
    error "Docker up failed!"
    exit 1
fi

# 4. 查看日志
log "Following logs (Ctrl+C to exit)..."
docker compose logs -f 
