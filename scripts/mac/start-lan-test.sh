#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
STATE_DIR="$PROJECT_ROOT/.lan-test"
PORTAL_DIR="$PROJECT_ROOT/portal"
PORTAL_RELEASE_DIR="$PORTAL_DIR/agent/release"
PORTAL_SCRIPT_DIR="$PORTAL_DIR/scripts"
AGENT_RELEASE_DIR="$PROJECT_ROOT/agent/release"
VERSION_HELPER="$PROJECT_ROOT/agent/scripts/get-agent-version.sh"
UPGRADER_JAR="$PROJECT_ROOT/upgrader/target/upgrader.jar"

API_PORT="${LIGHTSCRIPT_API_PORT:-8080}"
WEB_PORT="${LIGHTSCRIPT_WEB_PORT:-3001}"
PORTAL_PORT="${LIGHTSCRIPT_PORTAL_PORT:-8002}"
REGISTER_TOKEN="${LIGHTSCRIPT_REGISTER_TOKEN:-dev-register-token}"
SERVER_PROFILE="${LIGHTSCRIPT_SERVER_PROFILE:-dev}"

mkdir -p "$STATE_DIR"

detect_lan_ip() {
    if [ -n "${LIGHTSCRIPT_LAN_IP:-}" ]; then
        echo "$LIGHTSCRIPT_LAN_IP"
        return 0
    fi

    for iface in en0 en1 en5; do
        local ip
        ip="$(ipconfig getifaddr "$iface" 2>/dev/null || true)"
        if [ -n "$ip" ]; then
            echo "$ip"
            return 0
        fi
    done

    local fallback
    fallback="$(ifconfig | awk '/inet / && $2 !~ /^127\./ {print $2; exit}')"
    if [ -n "$fallback" ]; then
        echo "$fallback"
        return 0
    fi

    echo "❌ 无法自动识别局域网IP，请先设置 LIGHTSCRIPT_LAN_IP" >&2
    exit 1
}

wait_for_url() {
    local url="$1"
    local label="$2"
    local attempts="${3:-30}"

    for _ in $(seq 1 "$attempts"); do
        local status
        status="$(curl -s -o /dev/null -w "%{http_code}" "$url" 2>/dev/null || echo "000")"
        if [ "$status" = "200" ] || [ "$status" = "401" ] || [ "$status" = "302" ]; then
            echo "✅ $label 就绪: $url"
            return 0
        fi
        sleep 2
    done

    echo "❌ $label 启动超时: $url"
    return 1
}

stop_port() {
    local port="$1"
    local pids

    pids="$(lsof -ti "tcp:$port" 2>/dev/null || true)"
    if [ -z "$pids" ]; then
        return 0
    fi

    echo "🛑 释放端口 $port: $pids"
    kill $pids 2>/dev/null || true
    sleep 2

    pids="$(lsof -ti "tcp:$port" 2>/dev/null || true)"
    if [ -n "$pids" ]; then
        kill -9 $pids 2>/dev/null || true
    fi
}

ensure_web_deps() {
    if [ -d "$PROJECT_ROOT/web/node_modules" ]; then
        return 0
    fi

    echo "📦 安装 web 依赖..."
    (cd "$PROJECT_ROOT/web" && npm install)
}

ensure_agent_release() {
    local version="$1"

    if [ -f "$AGENT_RELEASE_DIR/version.json" ] && [ -f "$AGENT_RELEASE_DIR/viberemote-agent-$version-windows-x64.zip" ]; then
        return 0
    fi

    echo "📦 未发现当前版本安装包，开始构建 agent release..."
    (cd "$PROJECT_ROOT/agent" && bash build-release.sh)
}

ensure_upgrader() {
    if [ -f "$UPGRADER_JAR" ]; then
        return 0
    fi

    echo "📦 未发现 upgrader.jar，开始构建 upgrader..."
    (cd "$PROJECT_ROOT/upgrader" && mvn -q -DskipTests package)
}

write_local_installer() {
    local source_file="$1"
    local target_file="$2"
    local version="$3"
    local api_origin="$4"
    local register_token="$5"
    local package_origin="${6:-}"

    sed "s/__AGENT_VERSION__/${version}/g" "$source_file" > "$target_file"

    python3 - <<'PY' "$target_file" "$api_origin" "$register_token" "$package_origin"
from pathlib import Path
import sys

path = Path(sys.argv[1])
api_origin = sys.argv[2]
token = sys.argv[3]
package_origin = sys.argv[4]
text = path.read_text()
text = text.replace('__SERVER_URL__', api_origin)
text = text.replace('__REGISTER_TOKEN__', token)
if package_origin:
    text = text.replace('__PACKAGE_BASE_URL__', package_origin)
path.write_text(text)
PY
}

prepare_portal_assets() {
    local version="$1"
    local api_origin="$2"
    local portal_origin="$3"

    echo "📁 准备门户安装资源..."
    mkdir -p "$PORTAL_RELEASE_DIR"
    rsync -a --delete "$AGENT_RELEASE_DIR/" "$PORTAL_RELEASE_DIR/"

    write_local_installer \
        "$PROJECT_ROOT/agent/scripts/unix/install-linux.sh" \
        "$PORTAL_SCRIPT_DIR/viberemote-agent-$version-install-linux.sh" \
        "$version" \
        "$api_origin" \
        "$REGISTER_TOKEN" \
        "$portal_origin/agent/release"

    write_local_installer \
        "$PROJECT_ROOT/agent/scripts/unix/install-macos.sh" \
        "$PORTAL_SCRIPT_DIR/viberemote-agent-$version-install-macos.sh" \
        "$version" \
        "$api_origin" \
        "$REGISTER_TOKEN" \
        "$portal_origin/agent/release"

    write_local_installer \
        "$PROJECT_ROOT/agent/scripts/windows/install-agent.bat" \
        "$PORTAL_SCRIPT_DIR/viberemote-agent-$version-install-windows.bat" \
        "$version" \
        "$api_origin" \
        "$REGISTER_TOKEN" \
        "$portal_origin/agent/release"

    chmod +x \
        "$PORTAL_SCRIPT_DIR/viberemote-agent-$version-install-linux.sh" \
        "$PORTAL_SCRIPT_DIR/viberemote-agent-$version-install-macos.sh"

    python3 - <<'PY' "$PORTAL_SCRIPT_DIR/viberemote-agent-$version-install-windows.bat"
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text()
text = text.replace("\r\n", "\n")
path.write_bytes(text.replace("\n", "\r\n").encode("utf-8"))
PY

    echo "✅ 门户资源已同步到 $PORTAL_RELEASE_DIR"
}

start_server() {
    local api_origin="$1"

    echo "🚀 启动后端服务..."
    (
        cd "$PROJECT_ROOT/server"
        nohup mvn spring-boot:run \
            -Dspring-boot.run.profiles="$SERVER_PROFILE" \
            -Dspring-boot.run.jvmArguments="-Xmx1g -Xms512m" \
            -Dspring-boot.run.arguments="--server.address=0.0.0.0 --server.port=$API_PORT --lightscript.agent.public-base-url=$api_origin" \
            > "$STATE_DIR/server.log" 2>&1 &
        echo $! > "$STATE_DIR/server.pid"
    )
}

start_web() {
    echo "🚀 启动前端管理后台..."
    ensure_web_deps
    (
        cd "$PROJECT_ROOT/web"
        nohup npm run dev -- --host 0.0.0.0 --port "$WEB_PORT" \
            > "$STATE_DIR/web.log" 2>&1 &
        echo $! > "$STATE_DIR/web.pid"
    )
}

start_portal() {
    echo "🚀 启动门户网站..."
    (
        cd "$PORTAL_DIR"
        nohup python3 -m http.server "$PORTAL_PORT" --bind 0.0.0.0 \
            > "$STATE_DIR/portal.log" 2>&1 &
        echo $! > "$STATE_DIR/portal.pid"
    )
}

LAN_IP="$(detect_lan_ip)"
VERSION="$(bash "$VERSION_HELPER")"
API_ORIGIN="http://$LAN_IP:$API_PORT"
WEB_ORIGIN="http://$LAN_IP:$WEB_PORT"
PORTAL_ORIGIN="http://$LAN_IP:$PORTAL_PORT"

echo "========================================"
echo "LightScript 局域网测试环境启动"
echo "========================================"
echo "局域网IP:      $LAN_IP"
echo "Agent版本:     $VERSION"
echo "后端地址:      $API_ORIGIN"
echo "前端地址:      $WEB_ORIGIN"
echo "门户地址:      $PORTAL_ORIGIN"
echo "注册令牌:      $REGISTER_TOKEN"
echo ""

stop_port "$API_PORT"
stop_port "$WEB_PORT"
stop_port "$PORTAL_PORT"

ensure_agent_release "$VERSION"
prepare_portal_assets "$VERSION" "$API_ORIGIN" "$PORTAL_ORIGIN"

start_server "$API_ORIGIN"
wait_for_url "$API_ORIGIN/actuator/health" "后端服务" 40

start_web
wait_for_url "$WEB_ORIGIN/" "前端管理后台" 30

start_portal
wait_for_url "$PORTAL_ORIGIN/client-install.html" "门户网站" 20

echo ""
echo "🎉 局域网测试环境已启动"
echo "  门户安装页: $PORTAL_ORIGIN/client-install.html"
echo "  管理后台:   $WEB_ORIGIN/dashboard"
echo "  后端 API:   $API_ORIGIN"
echo ""
echo "Windows 测试机请访问:"
echo "  $PORTAL_ORIGIN/client-install.html"
echo ""
echo "日志文件:"
echo "  $STATE_DIR/server.log"
echo "  $STATE_DIR/web.log"
echo "  $STATE_DIR/portal.log"
