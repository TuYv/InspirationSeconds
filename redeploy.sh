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

# 2. 构建前端
log "Building frontend..."
cd frontend
npm install --silent
npm run build
if [ $? -ne 0 ]; then
    error "Frontend build failed!"
    exit 1
fi
cd ..

# 3. 部署前端静态文件
log "Deploying frontend to /var/www/wx-frontend/..."
rsync -a --delete frontend/dist/ /var/www/wx-frontend/
if [ $? -ne 0 ]; then
    error "Frontend deploy failed!"
    exit 1
fi

# 5. 构建镜像
log "Building Docker image..."
docker compose build
if [ $? -ne 0 ]; then
    error "Docker build failed!"
    exit 1
fi

# 6. 重启服务
log "Restarting services..."
docker compose up -d
if [ $? -ne 0 ]; then
    error "Docker up failed!"
    exit 1
fi

# 7. 查看日志
log "Following logs (Ctrl+C to exit)..."
docker compose logs -f 
