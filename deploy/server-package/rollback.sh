#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_ENV_FILE="$SCRIPT_DIR/deploy.env"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

info() {
    echo -e "${BLUE}[rollback]${NC} $1"
}

success() {
    echo -e "${GREEN}[rollback]${NC} $1"
}

warn() {
    echo -e "${YELLOW}[rollback]${NC} $1"
}

fail() {
    echo -e "${RED}[rollback] $1${NC}" >&2
    exit 1
}

start_or_reload_nginx() {
    local nginx_bin="$1"
    if pgrep -x nginx >/dev/null 2>&1; then
        "$nginx_bin" -s reload
    else
        "$nginx_bin"
    fi
}

main() {
    [ -f "$DEPLOY_ENV_FILE" ] || fail "缺少 deploy.env"
    # shellcheck disable=SC1090
    source "$DEPLOY_ENV_FILE"

    local backup_root backup_dir run_dir pid_file bin_dir nginx_bin nginx_config_file
    backup_root="${APP_HOME}/backups"
    run_dir="${RUN_DIR:-${APP_HOME}/run}"
    pid_file="${run_dir}/${SERVICE_NAME}.pid"
    bin_dir="${APP_HOME}/bin"
    nginx_config_file="${NGINX_CONFIG_FILE:-/etc/nginx/conf.d/${APP_NAME}.conf}"
    nginx_bin="${NGINX_BIN:-$(command -v nginx || true)}"

    if [ $# -gt 0 ]; then
        backup_dir="$1"
    else
        backup_dir="$(find "$backup_root" -mindepth 1 -maxdepth 1 -type d | sort | tail -1)"
    fi

    [ -n "${backup_dir:-}" ] || fail "未找到可用备份目录"
    [ -d "$backup_dir" ] || fail "备份目录不存在: $backup_dir"

    info "使用备份回滚: $backup_dir"

    if [ -f "${bin_dir}/stop-backend.sh" ]; then
        /bin/bash "${bin_dir}/stop-backend.sh" || true
    elif [ -f "$pid_file" ] && kill -0 "$(cat "$pid_file")" 2>/dev/null; then
        kill "$(cat "$pid_file")" 2>/dev/null || true
        rm -f "$pid_file"
    fi

    [ -d "$backup_dir/backend" ] && rm -rf "$BACKEND_DIR" && cp -a "$backup_dir/backend" "$BACKEND_DIR"
    [ -d "$backup_dir/frontend" ] && rm -rf "$FRONTEND_DIR" && cp -a "$backup_dir/frontend" "$FRONTEND_DIR"
    [ -d "$backup_dir/bin" ] && rm -rf "$bin_dir" && cp -a "$backup_dir/bin" "$bin_dir"
    [ -d "$backup_dir/portal-root" ] && find "$PORTAL_ROOT" -mindepth 1 -maxdepth 1 -exec rm -rf {} + && cp -a "$backup_dir/portal-root/." "$PORTAL_ROOT/"
    [ -f "$backup_dir/nginx.conf" ] && cp -a "$backup_dir/nginx.conf" "$nginx_config_file"

    if [ -n "$nginx_bin" ]; then
        start_or_reload_nginx "$nginx_bin"
    else
        warn "未检测到 nginx，已跳过 nginx 重载"
    fi

    if [ -f "${bin_dir}/start-backend.sh" ]; then
        /bin/bash "${bin_dir}/start-backend.sh"
    else
        warn "未检测到后端启动脚本，请手工启动后端"
    fi

    success "回滚完成"
}

main "$@"
