#!/bin/bash

# LightScript 阿里云一键部署脚本
# 用途：自动部署前后端到阿里云服务器

set -e

# 配置变量
SERVER_IP="8.138.114.34"
SERVER_USER="root"
REMOTE_DIR="/opt/lightscript"
PROJECT_NAME="LightScript"
BACKEND_PORT="8080"
FRONTEND_PORT="3000"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=========================================="
echo "LightScript 阿里云部署脚本"
echo -e "==========================================${NC}"
echo -e "服务器: ${GREEN}${SERVER_IP}${NC}"
echo -e "后端端口: ${GREEN}${BACKEND_PORT}${NC}"
echo -e "前端端口: ${GREEN}${FRONTEND_PORT}${NC}"
echo ""

# 检查SSH连接
echo -e "${YELLOW}🔍 检查SSH连接...${NC}"
if ! ssh -o BatchMode=yes -o ConnectTimeout=5 ${SERVER_USER}@${SERVER_IP} "echo '连接成功'" 2>/dev/null; then
    echo -e "${RED}❌ SSH连接失败！${NC}"
    echo -e "${YELLOW}请先运行: ./scripts/mac/setup-ssh-key.sh${NC}"
    exit 1
fi
echo -e "${GREEN}✅ SSH连接正常${NC}"
echo ""

# 本地构建
echo -e "${YELLOW}🔨 开始本地构建...${NC}"

# 构建后端
echo -e "${BLUE}📦 构建后端项目...${NC}"
mvn clean package -DskipTests
echo -e "${GREEN}✅ 后端构建完成${NC}"

# 构建前端
echo -e "${BLUE}📦 构建前端项目...${NC}"
cd ../web-modern
npm install
npm run build
cd ../server
echo -e "${GREEN}✅ 前端构建完成${NC}"

# 构建门户网站
echo -e "${BLUE}📦 准备门户网站...${NC}"
# 门户网站是静态文件，直接复制即可
echo -e "${GREEN}✅ 门户网站准备完成${NC}"
echo ""

# 创建部署包
echo -e "${YELLOW}📦 创建部署包...${NC}"
DEPLOY_DIR="deploy-$(date +%Y%m%d-%H%M%S)"
mkdir -p ${DEPLOY_DIR}

# 复制后端文件
mkdir -p ${DEPLOY_DIR}/backend
cp target/server-*.jar ${DEPLOY_DIR}/backend/server.jar
cp -r src/main/resources/application*.yml ${DEPLOY_DIR}/backend/ 2>/dev/null || true

# 复制前端构建文件
mkdir -p ${DEPLOY_DIR}/frontend
cp -r ../web-modern/dist/* ${DEPLOY_DIR}/frontend/

# 复制门户网站文件
mkdir -p ${DEPLOY_DIR}/portal
cp -r ../portal/* ${DEPLOY_DIR}/portal/

# 复制启动脚本
mkdir -p ${DEPLOY_DIR}/scripts
cat > ${DEPLOY_DIR}/scripts/start-backend.sh << 'EOF'
#!/bin/bash
cd /opt/lightscript/backend
nohup java -jar server.jar --spring.profiles.active=prod > backend.log 2>&1 &
echo $! > backend.pid
echo "后端服务已启动，PID: $(cat backend.pid)"
EOF

cat > ${DEPLOY_DIR}/scripts/start-frontend.sh << 'EOF'
#!/bin/bash
# 前端使用Nginx部署，无需单独启动
systemctl start nginx
systemctl enable nginx
echo "前端服务已启动（Nginx）"
EOF

cat > ${DEPLOY_DIR}/scripts/stop-all.sh << 'EOF'
#!/bin/bash
# 停止后端
if [ -f /opt/lightscript/backend/backend.pid ]; then
    kill $(cat /opt/lightscript/backend/backend.pid) 2>/dev/null || true
    rm -f /opt/lightscript/backend/backend.pid
    echo "后端服务已停止"
fi

# 停止前端（Nginx由systemd管理，不在这里停止）
echo "前端服务由Nginx提供，使用 systemctl stop nginx 停止"
EOF

cat > ${DEPLOY_DIR}/scripts/restart-all.sh << 'EOF'
#!/bin/bash
cd /opt/lightscript/scripts
./stop-all.sh
sleep 2
./start-backend.sh
sleep 3
systemctl restart nginx
echo "所有服务已重启"
EOF

chmod +x ${DEPLOY_DIR}/scripts/*.sh

echo -e "${GREEN}✅ 部署包创建完成${NC}"
echo ""

# 上传到服务器
echo -e "${YELLOW}📤 上传文件到服务器...${NC}"

# 创建远程目录
ssh ${SERVER_USER}@${SERVER_IP} "mkdir -p ${REMOTE_DIR}"

# 停止现有服务
echo -e "${BLUE}🛑 停止现有服务...${NC}"
ssh ${SERVER_USER}@${SERVER_IP} "
    if [ -f ${REMOTE_DIR}/scripts/stop-all.sh ]; then
        cd ${REMOTE_DIR}/scripts && ./stop-all.sh
    fi
" || true

# 备份现有部署
ssh ${SERVER_USER}@${SERVER_IP} "
    if [ -d ${REMOTE_DIR}/backend ]; then
        mv ${REMOTE_DIR} ${REMOTE_DIR}.backup.$(date +%Y%m%d-%H%M%S) || true
    fi
"

# 上传新文件
echo -e "${BLUE}📤 上传部署包...${NC}"
scp -r ${DEPLOY_DIR}/* ${SERVER_USER}@${SERVER_IP}:${REMOTE_DIR}/

# 清理本地部署包
rm -rf ${DEPLOY_DIR}

echo -e "${GREEN}✅ 文件上传完成${NC}"
echo ""

# 服务器端配置
echo -e "${YELLOW}⚙️  配置服务器环境...${NC}"

ssh ${SERVER_USER}@${SERVER_IP} << 'ENDSSH'
# 创建必要的目录
mkdir -p /opt/lightscript/logs

# 检查Java
if ! command -v java &> /dev/null; then
    echo "安装Java..."
    yum install -y java-11-openjdk || apt-get install -y openjdk-11-jdk
fi

# 检查Nginx
if ! command -v nginx &> /dev/null; then
    echo "安装Nginx..."
    yum install -y nginx || apt-get install -y nginx
fi

# 配置Nginx
cat > /etc/nginx/conf.d/lightscript.conf << 'NGINXCONF'
# 门户网站 (主站)
server {
    listen 80 default_server;
    server_name _;
    
    root /opt/lightscript/portal;
    index index.html;
    
    location / {
        try_files $uri $uri/ /index.html;
    }
    
    # 管理后台
    location /admin/ {
        alias /opt/lightscript/frontend/;
        try_files $uri $uri/ /index.html;
    }
    
    # 后端API代理
    location /api/ {
        proxy_pass http://localhost:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
    
    # 静态资源缓存
    location ~* \.(css|js|png|jpg|jpeg|gif|ico|svg)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }
    
    access_log /opt/lightscript/logs/nginx-access.log;
    error_log /opt/lightscript/logs/nginx-error.log;
}

# 管理后台专用端口 (3000)
server {
    listen 3000;
    server_name _;
    
    root /opt/lightscript/frontend;
    index index.html;
    
    location / {
        try_files $uri $uri/ /index.html;
    }
    
    # 后端API代理
    location /api/ {
        proxy_pass http://localhost:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
    
    location /assets/ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }
    
    access_log /opt/lightscript/logs/nginx-admin-access.log;
    error_log /opt/lightscript/logs/nginx-admin-error.log;
}
NGINXCONF

# 测试Nginx配置
nginx -t

# 启动并启用Nginx
systemctl enable nginx
systemctl restart nginx

# 配置防火墙
if command -v firewall-cmd &> /dev/null; then
    echo "配置防火墙..."
    firewall-cmd --permanent --add-port=8080/tcp || true
    firewall-cmd --permanent --add-port=3000/tcp || true
    firewall-cmd --permanent --add-service=http || true
    firewall-cmd --reload || true
fi

echo "✅ 服务器环境配置完成"
ENDSSH

echo -e "${GREEN}✅ 服务器配置完成${NC}"
echo ""

# 启动服务
echo -e "${YELLOW}🚀 启动服务...${NC}"

ssh ${SERVER_USER}@${SERVER_IP} "
    cd ${REMOTE_DIR}/scripts
    ./start-backend.sh
    sleep 5
    ./start-frontend.sh
"

echo ""
echo -e "${GREEN}=========================================="
echo "✅ 部署完成！"
echo -e "==========================================${NC}"
echo ""
echo -e "${BLUE}访问地址：${NC}"
echo -e "  门户网站: ${GREEN}http://${SERVER_IP}${NC} (主站)"
echo -e "  管理后台: ${GREEN}http://${SERVER_IP}/admin${NC} 或 ${GREEN}http://${SERVER_IP}:${FRONTEND_PORT}${NC}"
echo -e "  后端API: ${GREEN}http://${SERVER_IP}:${BACKEND_PORT}${NC}"
echo ""
echo -e "${BLUE}管理命令：${NC}"
echo -e "  查看后端日志: ${YELLOW}ssh ${SERVER_USER}@${SERVER_IP} 'tail -f ${REMOTE_DIR}/backend/backend.log'${NC}"
echo -e "  查看Nginx日志: ${YELLOW}ssh ${SERVER_USER}@${SERVER_IP} 'tail -f ${REMOTE_DIR}/logs/nginx-access.log'${NC}"
echo -e "  重启服务: ${YELLOW}ssh ${SERVER_USER}@${SERVER_IP} '${REMOTE_DIR}/scripts/restart-all.sh'${NC}"
echo -e "  停止服务: ${YELLOW}ssh ${SERVER_USER}@${SERVER_IP} '${REMOTE_DIR}/scripts/stop-all.sh'${NC}"
echo ""
echo -e "${BLUE}默认登录账号：${NC}"
echo -e "  管理员: ${GREEN}admin / admin123${NC}"
echo -e "  普通用户: ${GREEN}user / user123${NC}"
echo ""
