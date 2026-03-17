#!/bin/bash

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

# 2. 构建 image-frontend
log "Building image-frontend..."
cd image-frontend
npm install --silent
npm run build
if [ $? -ne 0 ]; then
    error "image-frontend build failed!"
    exit 1
fi
cd ..

# 3. 部署静态文件
log "Deploying image-frontend to /var/www/image-frontend/..."
mkdir -p /var/www/image-frontend
rsync -a --delete image-frontend/dist/ /var/www/image-frontend/
if [ $? -ne 0 ]; then
    error "Deploy failed!"
    exit 1
fi

log "Done. image.soloship.top updated."
