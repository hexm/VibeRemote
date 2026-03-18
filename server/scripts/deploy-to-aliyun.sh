#!/bin/bash

# LightScript 阿里云一键全量部署脚本
# 部署内容：后端 jar + prod配置 + 前端 + 门户 + agent安装包
# 部署完成后在服务器上重装 agent

set -e

SERVER_IP="8.138.114.34"
SERVER_USER="root"
REMOTE_DIR="/opt/lightscript"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}=========================================="
echo "LightScript 阿里云全量部署"
echo -e "==========================================${NC}"
echo -e "服务器: ${GREEN}${SERVER_IP}${NC}"
echo ""

# 检查SSH连接
echo -e "${YELLOW}🔍 检查SSH连接...${NC}"
if ! ssh -o BatchMode=yes -o ConnectTimeout=5 ${SERVER_USER}@${SERVER_IP} "echo ok" 2>/dev/null; then
    echo -e "${RED}❌ SSH连接失败${NC}"
    exit 1
fi
echo -e "${GREEN}✅ SSH连接正常${NC}"
echo ""

# ============================================================
# 第一步：构建后端
# ============================================================
echo -e "${YELLOW}📦 [1/5] 构建后端...${NC}"
cd "$PROJECT_ROOT/server"
mvn clean package -DskipTests -q
JAR_FILE=$(ls "$PROJECT_ROOT/server/target/server-"*.jar | head -1)
echo -e "${GREEN}✅ 后端构建完成: $(basename $JAR_FILE)${NC}"
echo ""

# ============================================================
# 第二步：构建前端
# ============================================================
echo -e "${YELLOW}📦 [2/5] 构建前端...${NC}"
cd "$PROJECT_ROOT/web"
npm install --silent
npm run build
echo -e "${GREEN}✅ 前端构建完成${NC}"
echo ""

# ============================================================
# 第三步：部署后端 + 前端 + 门户
# ============================================================
echo -e "${YELLOW}📤 [3/5] 部署后端 + 前端 + 门户...${NC}"

# 创建远程目录
ssh ${SERVER_USER}@${SERVER_IP} "mkdir -p ${REMOTE_DIR}/backend ${REMOTE_DIR}/frontend ${REMOTE_DIR}/portal ${REMOTE_DIR}/logs/tasks"

# 停止后端
echo "停止后端服务..."
ssh ${SERVER_USER}@${SERVER_IP} "bash ${REMOTE_DIR}/scripts/start-server-aliyun.sh stop 2>/dev/null || true" || true

# 上传后端 jar
echo "上传后端 jar..."
scp "$JAR_FILE" ${SERVER_USER}@${SERVER_IP}:${REMOTE_DIR}/backend/server-0.4.0.jar

# 上传 prod 配置（每次部署都覆盖，确保配置最新）
echo "上传 application-prod.yml..."
scp "$PROJECT_ROOT/server/src/main/resources/application-prod.yml" ${SERVER_USER}@${SERVER_IP}:${REMOTE_DIR}/backend/

# 上传启动脚本
echo "上传启动脚本..."
ssh ${SERVER_USER}@${SERVER_IP} "mkdir -p ${REMOTE_DIR}/scripts"
scp "$PROJECT_ROOT/server/scripts/start-server-aliyun.sh" ${SERVER_USER}@${SERVER_IP}:${REMOTE_DIR}/scripts/
ssh ${SERVER_USER}@${SERVER_IP} "chmod +x ${REMOTE_DIR}/scripts/start-server-aliyun.sh"

# 上传前端
echo "上传前端..."
ssh ${SERVER_USER}@${SERVER_IP} "rm -rf ${REMOTE_DIR}/frontend/*"
scp -r "$PROJECT_ROOT/web/dist/." ${SERVER_USER}@${SERVER_IP}:${REMOTE_DIR}/frontend/

# 上传门户
echo "上传门户..."
ssh ${SERVER_USER}@${SERVER_IP} "rm -rf ${REMOTE_DIR}/portal/*"
scp -r "$PROJECT_ROOT/portal/." ${SERVER_USER}@${SERVER_IP}:${REMOTE_DIR}/portal/

# 启动后端
echo "启动后端服务..."
ssh ${SERVER_USER}@${SERVER_IP} "bash ${REMOTE_DIR}/scripts/start-server-aliyun.sh start"

echo -e "${GREEN}✅ 后端 + 前端 + 门户部署完成${NC}"
echo ""

# ============================================================
# 第四步：构建并上传 agent 安装包
# ============================================================
echo -e "${YELLOW}📦 [4/5] 构建 agent 安装包...${NC}"
cd "$PROJECT_ROOT/agent"
bash build-release.sh
echo -e "${GREEN}✅ agent 安装包构建完成${NC}"
echo ""

echo -e "${YELLOW}📤 上传 agent 安装包...${NC}"
bash "$PROJECT_ROOT/agent/scripts/deploy-agent-packages.sh" --yes
echo -e "${GREEN}✅ agent 安装包上传完成${NC}"
echo ""

# ============================================================
# 第五步：服务器上重装 agent
# ============================================================
echo -e "${YELLOW}🤖 [5/5] 服务器上重装 agent...${NC}"
ssh ${SERVER_USER}@${SERVER_IP} "curl -fsSL http://${SERVER_IP}/scripts/install-linux.sh | bash -s -- --server=http://${SERVER_IP}:8080"
echo -e "${GREEN}✅ agent 重装完成${NC}"
echo ""

echo -e "${GREEN}=========================================="
echo "✅ 全量部署完成！"
echo -e "==========================================${NC}"
echo ""
echo -e "  门户网站: ${GREEN}http://${SERVER_IP}${NC}"
echo -e "  管理后台: ${GREEN}http://${SERVER_IP}:3000${NC}"
echo -e "  后端API:  ${GREEN}http://${SERVER_IP}:8080${NC}"
echo ""
echo -e "  查看后端日志: ${YELLOW}ssh ${SERVER_USER}@${SERVER_IP} 'tail -f ${REMOTE_DIR}/backend/backend.log'${NC}"
