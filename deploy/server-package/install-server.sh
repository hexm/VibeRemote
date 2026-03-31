#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_ENV_FILE="$SCRIPT_DIR/deploy.env"
PACKAGE_INFO_FILE="$SCRIPT_DIR/PACKAGE_INFO"
AGENT_VERSION_FILE="$SCRIPT_DIR/agent/release/version.json"
CURRENT_USER="$(id -un)"
CURRENT_GROUP="$(id -gn)"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

info() {
    echo -e "${BLUE}[install]${NC} $1"
}

success() {
    echo -e "${GREEN}[install]${NC} $1"
}

warn() {
    echo -e "${YELLOW}[install]${NC} $1"
}

fail() {
    echo -e "${RED}[install] $1${NC}" >&2
    exit 1
}

trim_trailing_slash() {
    local value="$1"
    while [ "${value%/}" != "$value" ]; do
        value="${value%/}"
    done
    printf '%s' "$value"
}

generate_random_secret() {
    if command -v openssl >/dev/null 2>&1; then
        openssl rand -base64 48 | tr -d '\n'
        return
    fi

    if [ -r /dev/urandom ] && command -v base64 >/dev/null 2>&1; then
        head -c 48 /dev/urandom | base64 | tr -d '\n'
        return
    fi

    printf '%s%s%s' "$(date +%s)" "$RANDOM" "$(uuidgen 2>/dev/null || echo fallback-secret)"
}

persist_generated_env() {
    mkdir -p "$(dirname "$GENERATED_ENV_FILE")"
    cat > "$GENERATED_ENV_FILE" <<EOF
JWT_SECRET='${JWT_SECRET}'
EOF
    chmod 600 "$GENERATED_ENV_FILE"
}

require_file() {
    local file="$1"
    [ -f "$file" ] || fail "缺少文件: $file"
}

escape_sed() {
    printf '%s' "$1" | sed -e 's/[\/&\\]/\\&/g'
}

run_as_app_user() {
    local command="$1"
    if [ "$CURRENT_USER" = "$APP_USER" ]; then
        /bin/bash -lc "$command"
    elif [ "${EUID}" -eq 0 ]; then
        su - "$APP_USER" -c "$command"
    else
        fail "当前用户 ${CURRENT_USER} 无法切换到 APP_USER=${APP_USER}，请改用目标用户执行部署脚本或使用 root"
    fi
}

chown_if_possible() {
    if [ "${EUID}" -eq 0 ]; then
        chown -R "$APP_USER:$APP_GROUP" "$@"
    elif [ "$CURRENT_USER" != "$APP_USER" ]; then
        warn "当前不是 ${APP_USER} 用户，已跳过 chown，请确认目标目录权限"
    fi
}

render_template() {
    local src="$1"
    local dst="$2"

    sed \
        -e "s/__BACKEND_DIR__/$(escape_sed "$BACKEND_DIR")/g" \
        -e "s/__FRONTEND_DIR__/$(escape_sed "$FRONTEND_DIR")/g" \
        -e "s/__PORTAL_ROOT__/$(escape_sed "$PORTAL_ROOT")/g" \
        -e "s/__PORTAL_PUBLIC_BASE_URL__/$(escape_sed "$PORTAL_PUBLIC_BASE_URL")/g" \
        -e "s/__ADMIN_PUBLIC_BASE_URL__/$(escape_sed "$ADMIN_PUBLIC_BASE_URL")/g" \
        -e "s/__PORTAL_LISTEN_PORT__/$(escape_sed "$PORTAL_LISTEN_PORT")/g" \
        -e "s/__ADMIN_LISTEN_PORT__/$(escape_sed "$ADMIN_LISTEN_PORT")/g" \
        -e "s/__API_PORT__/$(escape_sed "$API_PORT")/g" \
        -e "s/__NGINX_SERVER_NAME__/$(escape_sed "$NGINX_SERVER_NAME")/g" \
        -e "s/__MAX_UPLOAD_SIZE_MB__/$(escape_sed "$MAX_UPLOAD_SIZE_MB")/g" \
        -e "s/__DB_URL__/$(escape_sed "$DB_URL")/g" \
        -e "s/__DB_USERNAME__/$(escape_sed "$DB_USERNAME")/g" \
        -e "s/__DB_PASSWORD__/$(escape_sed "$DB_PASSWORD")/g" \
        -e "s/__LOG_DIR__/$(escape_sed "$LOG_DIR")/g" \
        -e "s/__TASK_LOG_STORAGE_PATH__/$(escape_sed "$TASK_LOG_STORAGE_PATH")/g" \
        -e "s/__LOG_RETENTION_DAYS__/$(escape_sed "$LOG_RETENTION_DAYS")/g" \
        -e "s/__JWT_SECRET__/$(escape_sed "$JWT_SECRET")/g" \
        -e "s/__REGISTER_TOKEN__/$(escape_sed "$REGISTER_TOKEN")/g" \
        -e "s/__WEB_ENCRYPTION_KEY__/$(escape_sed "$WEB_ENCRYPTION_KEY")/g" \
        -e "s/__API_PUBLIC_BASE_URL__/$(escape_sed "$API_PUBLIC_BASE_URL")/g" \
        "$src" > "$dst"
}

load_env() {
    require_file "$DEPLOY_ENV_FILE"
    # shellcheck disable=SC1090
    source "$DEPLOY_ENV_FILE"

    : "${APP_NAME:?APP_NAME 未配置}"
    : "${SERVICE_NAME:?SERVICE_NAME 未配置}"
    : "${APP_HOME:?APP_HOME 未配置}"
    : "${BACKEND_DIR:?BACKEND_DIR 未配置}"
    : "${FRONTEND_DIR:?FRONTEND_DIR 未配置}"
    : "${LOG_DIR:?LOG_DIR 未配置}"
    : "${TASK_LOG_STORAGE_PATH:?TASK_LOG_STORAGE_PATH 未配置}"
    : "${PORTAL_ROOT:?PORTAL_ROOT 未配置}"
    : "${PORTAL_LISTEN_PORT:?PORTAL_LISTEN_PORT 未配置}"
    : "${ADMIN_LISTEN_PORT:?ADMIN_LISTEN_PORT 未配置}"
    : "${API_PORT:?API_PORT 未配置}"
    : "${NGINX_SERVER_NAME:?NGINX_SERVER_NAME 未配置}"
    : "${PORTAL_PUBLIC_BASE_URL:?PORTAL_PUBLIC_BASE_URL 未配置}"
    : "${ADMIN_PUBLIC_BASE_URL:?ADMIN_PUBLIC_BASE_URL 未配置}"
    : "${API_PUBLIC_BASE_URL:?API_PUBLIC_BASE_URL 未配置}"
    : "${DB_URL:?DB_URL 未配置}"
    : "${DB_USERNAME:?DB_USERNAME 未配置}"
    : "${DB_PASSWORD:?DB_PASSWORD 未配置}"
    : "${REGISTER_TOKEN:?REGISTER_TOKEN 未配置}"
    : "${WEB_ENCRYPTION_KEY:?WEB_ENCRYPTION_KEY 未配置}"
    : "${LOG_RETENTION_DAYS:?LOG_RETENTION_DAYS 未配置}"
    : "${MAX_UPLOAD_SIZE_MB:?MAX_UPLOAD_SIZE_MB 未配置}"

    APP_USER="${APP_USER:-$CURRENT_USER}"
    APP_GROUP="${APP_GROUP:-$CURRENT_GROUP}"
    RUN_DIR="${RUN_DIR:-${APP_HOME}/run}"
    NGINX_CONFIG_FILE="${NGINX_CONFIG_FILE:-/etc/nginx/conf.d/${APP_NAME}.conf}"
    NGINX_BIN="${NGINX_BIN:-}"
    GENERATED_ENV_FILE="${GENERATED_ENV_FILE:-${APP_HOME}/deploy-generated.env}"

    if [ -z "${JWT_SECRET:-}" ] && [ -f "$GENERATED_ENV_FILE" ]; then
        # shellcheck disable=SC1090
        source "$GENERATED_ENV_FILE"
    fi

    if [ -z "${AGENT_PACKAGE_BASE_URL:-}" ]; then
        AGENT_PACKAGE_BASE_URL="$(trim_trailing_slash "$PORTAL_PUBLIC_BASE_URL")/agent/release"
        info "未配置 AGENT_PACKAGE_BASE_URL，已自动推导为: ${AGENT_PACKAGE_BASE_URL}"
    fi

    if [ -z "${JWT_SECRET:-}" ]; then
        JWT_SECRET="$(generate_random_secret)"
        persist_generated_env
        info "未配置 JWT_SECRET，已自动生成并保存到: ${GENERATED_ENV_FILE}"
    fi

    BACKUP_ROOT="${APP_HOME}/backups"
    LAST_BACKUP_FILE="${APP_HOME}/.last-backup"
    DOWNLOADS_DIR="${PORTAL_ROOT}/downloads"
    BIN_DIR="${APP_HOME}/bin"
    BACKEND_PID_FILE="${RUN_DIR}/${SERVICE_NAME}.pid"
    BACKEND_STDOUT_LOG="${LOG_DIR}/${SERVICE_NAME}-console.log"
    BACKEND_START_SCRIPT="${BIN_DIR}/start-backend.sh"
    BACKEND_STOP_SCRIPT="${BIN_DIR}/stop-backend.sh"
    BACKEND_STATUS_SCRIPT="${BIN_DIR}/status-backend.sh"
}

load_package_info() {
    require_file "$PACKAGE_INFO_FILE"
    # shellcheck disable=SC1090
    source "$PACKAGE_INFO_FILE"

    : "${PACKAGE_ID:?PACKAGE_ID 未配置}"
    : "${PACKAGE_BUILT_AT:?PACKAGE_BUILT_AT 未配置}"
    : "${SERVER_VERSION:?SERVER_VERSION 未配置}"
    : "${AGENT_VERSION:?AGENT_VERSION 未配置}"
}

extract_agent_version() {
    sed -n 's/.*"version"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$AGENT_VERSION_FILE" | head -1
}

check_java() {
    JAVA_BIN="$(command -v java || true)"
    [ -n "$JAVA_BIN" ] || fail "未检测到 Java"

    local java_version
    java_version="$("$JAVA_BIN" -version 2>&1 | head -1)"
    echo "$java_version" | grep -Eq '"1\.8\.| version "8' || fail "当前 Java 不是 8: $java_version"
    success "Java 检测通过: $java_version"
}

check_nginx() {
    if [ -n "$NGINX_BIN" ]; then
        [ -x "$NGINX_BIN" ] || fail "NGINX_BIN 不可执行: $NGINX_BIN"
    else
        NGINX_BIN="$(command -v nginx || true)"
        [ -n "$NGINX_BIN" ] || fail "未检测到 Nginx"
    fi
    success "Nginx 检测通过: $("$NGINX_BIN" -v 2>&1)"
}

parse_db_host_port() {
    local jdbc_without_prefix host_port_db host_port
    jdbc_without_prefix="${DB_URL#jdbc:mysql://}"
    host_port_db="${jdbc_without_prefix%%\?*}"
    host_port="${host_port_db%%/*}"

    DB_HOST="${host_port%%:*}"
    DB_PORT="${host_port##*:}"

    if [ "$DB_HOST" = "$DB_PORT" ]; then
        DB_PORT="3306"
    fi
}

check_database() {
    parse_db_host_port
    if ! bash -c "exec 3<>/dev/tcp/${DB_HOST}/${DB_PORT}" 2>/dev/null; then
        fail "数据库地址不可达: ${DB_HOST}:${DB_PORT}"
    fi
    success "数据库网络连通性检测通过: ${DB_HOST}:${DB_PORT}"

    if command -v mysql >/dev/null 2>&1; then
        if ! MYSQL_PWD="$DB_PASSWORD" mysql --connect-timeout=5 -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USERNAME" -e "SELECT 1" >/dev/null 2>&1; then
            fail "数据库认证失败，请检查 DB_URL / DB_USERNAME / DB_PASSWORD"
        fi
        success "数据库账号认证通过"
    else
        warn "未检测到 mysql 客户端，已跳过数据库账号认证，仅保留网络连通性检测"
    fi
}

check_user_group() {
    id "$APP_USER" >/dev/null 2>&1 || fail "部署用户不存在: $APP_USER"
    if ! id -Gn "$APP_USER" | tr ' ' '\n' | grep -Fx "$APP_GROUP" >/dev/null 2>&1; then
        fail "部署组不存在或不属于用户 ${APP_USER}: $APP_GROUP"
    fi
    success "部署用户/组检测通过: ${APP_USER}:${APP_GROUP}"
}

check_package_layout() {
    require_file "$PACKAGE_INFO_FILE"
    require_file "$SCRIPT_DIR/backend/server.jar"
    require_file "$SCRIPT_DIR/frontend/dist/index.html"
    require_file "$SCRIPT_DIR/portal/site/index.html"
    require_file "$SCRIPT_DIR/agent/release/version.json"
    require_file "$SCRIPT_DIR/agent/installer-templates/install-linux.sh"
    require_file "$SCRIPT_DIR/agent/installer-templates/install-macos.sh"
    require_file "$SCRIPT_DIR/agent/installer-templates/install-windows.bat"
    require_file "$SCRIPT_DIR/templates/application-prod.yml.template"
    require_file "$SCRIPT_DIR/templates/nginx-viberemote.conf.template"
    require_file "$SCRIPT_DIR/templates/portal-runtime-config.js.template"
    success "部署包结构检测通过"
}

create_directories() {
    mkdir -p "$APP_HOME" "$BACKEND_DIR" "$FRONTEND_DIR" "$LOG_DIR" "$TASK_LOG_STORAGE_PATH" "$BACKUP_ROOT" "$RUN_DIR" "$BIN_DIR"
    mkdir -p "$PORTAL_ROOT" "$PORTAL_ROOT/agent/release" "$PORTAL_ROOT/scripts" "$DOWNLOADS_DIR"
    chown_if_possible "$APP_HOME" "$BACKEND_DIR" "$FRONTEND_DIR" "$LOG_DIR" "$TASK_LOG_STORAGE_PATH" "$RUN_DIR" "$BIN_DIR" "$PORTAL_ROOT"
}

create_backup() {
    BACKUP_DIR="${BACKUP_ROOT}/$(date '+%Y%m%d%H%M%S')"
    mkdir -p "$BACKUP_DIR"

    [ -d "$BACKEND_DIR" ] && cp -a "$BACKEND_DIR" "$BACKUP_DIR/backend" 2>/dev/null || true
    [ -d "$FRONTEND_DIR" ] && cp -a "$FRONTEND_DIR" "$BACKUP_DIR/frontend" 2>/dev/null || true
    [ -d "$PORTAL_ROOT" ] && cp -a "$PORTAL_ROOT" "$BACKUP_DIR/portal-root" 2>/dev/null || true
    [ -d "$BIN_DIR" ] && cp -a "$BIN_DIR" "$BACKUP_DIR/bin" 2>/dev/null || true
    [ -f "$NGINX_CONFIG_FILE" ] && cp -a "$NGINX_CONFIG_FILE" "$BACKUP_DIR/nginx.conf" 2>/dev/null || true

    echo "$BACKUP_DIR" > "$LAST_BACKUP_FILE"
    success "已创建备份: $BACKUP_DIR"
}

install_backend_control_scripts() {
    info "生成后端启停脚本..."

    cat > "$BACKEND_START_SCRIPT" <<EOF
#!/bin/bash
set -euo pipefail
PID_FILE='${BACKEND_PID_FILE}'
LOG_FILE='${BACKEND_STDOUT_LOG}'
BACKEND_DIR='${BACKEND_DIR}'
JAVA_BIN='${JAVA_BIN}'

if [ -f "\$PID_FILE" ] && kill -0 "\$(cat "\$PID_FILE")" 2>/dev/null; then
    echo "后端已在运行，PID: \$(cat "\$PID_FILE")"
    exit 0
fi

mkdir -p "\$(dirname "\$PID_FILE")" "\$(dirname "\$LOG_FILE")"
cd "\$BACKEND_DIR"
nohup "\$JAVA_BIN" -jar "\$BACKEND_DIR/server.jar" --spring.profiles.active=prod --spring.config.additional-location=file:\$BACKEND_DIR/ >> "\$LOG_FILE" 2>&1 &
echo \$! > "\$PID_FILE"
echo "后端已启动，PID: \$(cat "\$PID_FILE")"
EOF

    cat > "$BACKEND_STOP_SCRIPT" <<EOF
#!/bin/bash
set -euo pipefail
PID_FILE='${BACKEND_PID_FILE}'

if [ ! -f "\$PID_FILE" ]; then
    echo "后端未运行"
    exit 0
fi

PID="\$(cat "\$PID_FILE")"
if ! kill -0 "\$PID" 2>/dev/null; then
    rm -f "\$PID_FILE"
    echo "后端未运行"
    exit 0
fi

kill "\$PID" 2>/dev/null || true
for _ in 1 2 3 4 5 6 7 8 9 10; do
    if ! kill -0 "\$PID" 2>/dev/null; then
        rm -f "\$PID_FILE"
        echo "后端已停止"
        exit 0
    fi
    sleep 1
done

kill -9 "\$PID" 2>/dev/null || true
rm -f "\$PID_FILE"
echo "后端已强制停止"
EOF

    cat > "$BACKEND_STATUS_SCRIPT" <<EOF
#!/bin/bash
set -euo pipefail
PID_FILE='${BACKEND_PID_FILE}'

if [ -f "\$PID_FILE" ] && kill -0 "\$(cat "\$PID_FILE")" 2>/dev/null; then
    echo "RUNNING \$(cat "\$PID_FILE")"
    exit 0
fi

echo "STOPPED"
exit 1
EOF

    chmod +x "$BACKEND_START_SCRIPT" "$BACKEND_STOP_SCRIPT" "$BACKEND_STATUS_SCRIPT"
    chown_if_possible "$BACKEND_START_SCRIPT" "$BACKEND_STOP_SCRIPT" "$BACKEND_STATUS_SCRIPT"
}

install_backend() {
    info "部署后端..."
    cp "$SCRIPT_DIR/backend/server.jar" "$BACKEND_DIR/server.jar"
    render_template "$SCRIPT_DIR/templates/application-prod.yml.template" "$BACKEND_DIR/application-prod.yml"
    install_backend_control_scripts
    chown_if_possible "$BACKEND_DIR" "$LOG_DIR"
}

install_frontend() {
    info "部署控制台前端..."
    rm -rf "$FRONTEND_DIR"/*
    cp -R "$SCRIPT_DIR/frontend/dist/." "$FRONTEND_DIR/"
    chown_if_possible "$FRONTEND_DIR"
}

install_portal() {
    info "部署门户..."
    find "$PORTAL_ROOT" -mindepth 1 -maxdepth 1 ! -name downloads -exec rm -rf {} +
    cp -R "$SCRIPT_DIR/portal/site/." "$PORTAL_ROOT/"
    render_template "$SCRIPT_DIR/templates/portal-runtime-config.js.template" "$PORTAL_ROOT/runtime-config.js"
    mkdir -p "$PORTAL_ROOT/agent/release" "$PORTAL_ROOT/scripts"
    chown_if_possible "$PORTAL_ROOT"
}

publish_deploy_package() {
    local package_archive latest_archive manifest_file package_parent package_name
    package_archive="${DOWNLOADS_DIR}/${PACKAGE_ID}.tar.gz"
    latest_archive="${DOWNLOADS_DIR}/viberemote-server-deploy-package-latest.tar.gz"
    manifest_file="${DOWNLOADS_DIR}/server-deploy-manifest.json"
    package_parent="$(dirname "$SCRIPT_DIR")"
    package_name="$(basename "$SCRIPT_DIR")"

    info "发布服务端部署包下载资源..."
    rm -f "$package_archive" "$latest_archive"
    COPYFILE_DISABLE=1 tar -czf "$package_archive" -C "$package_parent" "$package_name"
    cp "$package_archive" "$latest_archive"

    cat > "$manifest_file" <<EOF
{
  "packageId": "${PACKAGE_ID}",
  "fileName": "$(basename "$package_archive")",
  "latestFileName": "$(basename "$latest_archive")",
  "downloadUrl": "/downloads/$(basename "$latest_archive")",
  "builtAt": "${PACKAGE_BUILT_AT}",
  "serverVersion": "${SERVER_VERSION}",
  "agentVersion": "${AGENT_VERSION}"
}
EOF
}

render_agent_installers() {
    local version
    version="$(extract_agent_version)"
    [ -n "$version" ] || fail "无法从 Agent 版本清单中解析版本号"

    info "发布 Agent 安装资源，版本: $version"
    rm -rf "$PORTAL_ROOT/agent/release"/*
    cp -R "$SCRIPT_DIR/agent/release/." "$PORTAL_ROOT/agent/release/"

    local linux_installer="$PORTAL_ROOT/scripts/viberemote-agent-${version}-install-linux.sh"
    local macos_installer="$PORTAL_ROOT/scripts/viberemote-agent-${version}-install-macos.sh"
    local windows_installer="$PORTAL_ROOT/scripts/viberemote-agent-${version}-install-windows.bat"

    sed \
        -e "s/__AGENT_VERSION__/$(escape_sed "$version")/g" \
        -e "s/__SERVER_URL__/$(escape_sed "$API_PUBLIC_BASE_URL")/g" \
        -e "s/__REGISTER_TOKEN__/$(escape_sed "$REGISTER_TOKEN")/g" \
        -e "s/__PACKAGE_BASE_URL__/$(escape_sed "$AGENT_PACKAGE_BASE_URL")/g" \
        "$SCRIPT_DIR/agent/installer-templates/install-linux.sh" > "$linux_installer"

    sed \
        -e "s/__AGENT_VERSION__/$(escape_sed "$version")/g" \
        -e "s/__SERVER_URL__/$(escape_sed "$API_PUBLIC_BASE_URL")/g" \
        -e "s/__REGISTER_TOKEN__/$(escape_sed "$REGISTER_TOKEN")/g" \
        -e "s/__PACKAGE_BASE_URL__/$(escape_sed "$AGENT_PACKAGE_BASE_URL")/g" \
        "$SCRIPT_DIR/agent/installer-templates/install-macos.sh" > "$macos_installer"

    sed \
        -e "s/__AGENT_VERSION__/$(escape_sed "$version")/g" \
        -e "s/__SERVER_URL__/$(escape_sed "$API_PUBLIC_BASE_URL")/g" \
        -e "s/__REGISTER_TOKEN__/$(escape_sed "$REGISTER_TOKEN")/g" \
        -e "s/__PACKAGE_BASE_URL__/$(escape_sed "$AGENT_PACKAGE_BASE_URL")/g" \
        "$SCRIPT_DIR/agent/installer-templates/install-windows.bat" > "${windows_installer}.tmp"

    awk '{ printf "%s\r\n", $0 }' "${windows_installer}.tmp" > "$windows_installer"
    rm -f "${windows_installer}.tmp"

    chmod +x "$linux_installer" "$macos_installer"
    chown_if_possible "$PORTAL_ROOT/agent" "$PORTAL_ROOT/scripts"
}

install_nginx() {
    info "安装 Nginx 配置..."
    mkdir -p "$(dirname "$NGINX_CONFIG_FILE")"
    render_template "$SCRIPT_DIR/templates/nginx-viberemote.conf.template" "$NGINX_CONFIG_FILE"
    "$NGINX_BIN" -t
}

reload_or_start_nginx() {
    info "重载并启动 Nginx..."
    if pgrep -x nginx >/dev/null 2>&1; then
        "$NGINX_BIN" -s reload
    else
        "$NGINX_BIN"
    fi
}

start_backend() {
    info "启动后端服务..."
    run_as_app_user "$BACKEND_START_SCRIPT"

    local health_url
    health_url="http://127.0.0.1:${API_PORT}/actuator/health"

    for _ in $(seq 1 30); do
        if ! run_as_app_user "$BACKEND_STATUS_SCRIPT" >/dev/null 2>&1; then
            fail "后端服务启动失败，请检查 ${BACKEND_STDOUT_LOG}"
        fi

        if curl -s -o /dev/null --max-time 3 "$health_url"; then
            success "后端健康检查已通过: ${health_url}"
            return
        fi

        sleep 2
    done

    fail "后端健康检查超时，请检查 ${BACKEND_STDOUT_LOG}"
}

run_verify() {
    info "执行部署验证..."
    bash "$SCRIPT_DIR/verify-deploy.sh"
}

print_summary() {
    cat <<EOF

${GREEN}==========================================${NC}
${GREEN}VibeRemote 服务端部署完成${NC}
${GREEN}==========================================${NC}

门户:      ${PORTAL_PUBLIC_BASE_URL}
控制台:    ${ADMIN_PUBLIC_BASE_URL}/dashboard
后端 API:  ${API_PUBLIC_BASE_URL}

后端控制脚本:
  启动: ${BACKEND_START_SCRIPT}
  停止: ${BACKEND_STOP_SCRIPT}
  状态: ${BACKEND_STATUS_SCRIPT}

默认管理员:
  用户名: admin
  密码:   admin123

备份目录:
  ${BACKUP_DIR}

EOF
}

main() {
    load_env
    load_package_info
    check_package_layout
    check_java
    check_nginx
    check_user_group
    check_database
    create_directories
    create_backup
    install_backend
    install_frontend
    install_portal
    render_agent_installers
    publish_deploy_package
    install_nginx
    reload_or_start_nginx
    start_backend
    run_verify
    print_summary
}

main "$@"
