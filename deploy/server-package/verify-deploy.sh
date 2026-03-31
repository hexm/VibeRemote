#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_ENV_FILE="$SCRIPT_DIR/deploy.env"
AGENT_VERSION_FILE="$SCRIPT_DIR/agent/release/version.json"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

info() {
    echo -e "${BLUE}[verify]${NC} $1"
}

success() {
    echo -e "${GREEN}[verify]${NC} $1"
}

warn() {
    echo -e "${YELLOW}[verify]${NC} $1"
}

fail() {
    echo -e "${RED}[verify] $1${NC}" >&2
    exit 1
}

http_code() {
    curl -s -o /dev/null -w "%{http_code}" "$1"
}

check_200() {
    local url="$1"
    local label="$2"
    local code
    code="$(http_code "$url")"
    [ "$code" = "200" ] || fail "${label} 检查失败: ${url} -> HTTP ${code}"
    success "${label} 检查通过: ${url}"
}

extract_agent_version() {
    sed -n 's/.*"version"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$AGENT_VERSION_FILE" | head -1
}

check_backend_process() {
    local pid_file="$1"
    [ -f "$pid_file" ] || fail "后端 PID 文件不存在: $pid_file"

    local pid
    pid="$(cat "$pid_file")"
    kill -0 "$pid" 2>/dev/null || fail "后端进程未运行，PID: $pid"
    success "后端进程运行正常: PID $pid"
}

main() {
    [ -f "$DEPLOY_ENV_FILE" ] || fail "缺少 deploy.env"
    # shellcheck disable=SC1090
    source "$DEPLOY_ENV_FILE"

    local run_dir pid_file
    run_dir="${RUN_DIR:-${APP_HOME}/run}"
    pid_file="${run_dir}/${SERVICE_NAME}.pid"

    local agent_version
    agent_version="$(extract_agent_version)"
    [ -n "$agent_version" ] || fail "无法解析 Agent 版本"

    info "开始验证服务状态..."

    check_backend_process "$pid_file"

    if pgrep -x nginx >/dev/null 2>&1; then
        success "Nginx 运行正常"
    else
        warn "未检测到 nginx 进程，继续通过 HTTP 接口验证"
    fi

    check_200 "http://127.0.0.1:${API_PORT}/actuator/health" "后端健康检查"
    check_200 "http://127.0.0.1:${ADMIN_LISTEN_PORT}/" "控制台前端"
    check_200 "http://127.0.0.1:${PORTAL_LISTEN_PORT}/" "门户首页"
    check_200 "http://127.0.0.1:${PORTAL_LISTEN_PORT}/agent/release/version.json" "Agent 版本清单"
    check_200 "http://127.0.0.1:${PORTAL_LISTEN_PORT}/scripts/viberemote-agent-${agent_version}-install-linux.sh" "Linux 一键安装脚本"
    check_200 "http://127.0.0.1:${PORTAL_LISTEN_PORT}/scripts/viberemote-agent-${agent_version}-install-macos.sh" "macOS 一键安装脚本"
    check_200 "http://127.0.0.1:${PORTAL_LISTEN_PORT}/scripts/viberemote-agent-${agent_version}-install-windows.bat" "Windows 一键安装脚本"

    echo ""
    success "全部验证通过"
}

main "$@"
