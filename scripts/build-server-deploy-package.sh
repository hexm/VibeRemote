#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PACKAGE_SOURCE_DIR="$PROJECT_ROOT/deploy/server-package"
OUTPUT_ROOT="$PROJECT_ROOT/build/server-deploy-package"
PORTAL_DOWNLOADS_DIR="$PROJECT_ROOT/portal/downloads"
AGENT_VERSION_HELPER="$PROJECT_ROOT/agent/scripts/get-agent-version.sh"
SERVER_VERSION="$(sed -n 's|.*<version>\(.*\)</version>.*|\1|p' "$PROJECT_ROOT/pom.xml" | head -1)"
AGENT_VERSION="$(bash "$AGENT_VERSION_HELPER")"
PACKAGE_TIMESTAMP="$(date '+%Y%m%d%H%M%S')"
PACKAGE_ID="viberemote-server-deploy-package-${PACKAGE_TIMESTAMP}"
PACKAGE_DIR="$OUTPUT_ROOT/$PACKAGE_ID"
PACKAGE_ARCHIVE="$OUTPUT_ROOT/${PACKAGE_ID}.tar.gz"
LATEST_ARCHIVE="$PORTAL_DOWNLOADS_DIR/viberemote-server-deploy-package-latest.tar.gz"
MANIFEST_FILE="$PORTAL_DOWNLOADS_DIR/server-deploy-manifest.json"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log() {
    echo -e "${BLUE}[server-package]${NC} $1"
}

warn() {
    echo -e "${YELLOW}[server-package]${NC} $1"
}

fail() {
    echo -e "${RED}[server-package] $1${NC}" >&2
    exit 1
}

require_file() {
    local file="$1"
    [ -f "$file" ] || fail "缺少文件: $file"
}

build_server() {
    log "构建后端..."
    (cd "$PROJECT_ROOT" && mvn -pl server -am -DskipTests package -q)
}

build_web() {
    log "构建控制台前端..."
    (cd "$PROJECT_ROOT/web" && npm run build)
}

ensure_agent_release() {
    local manifest="$PROJECT_ROOT/agent/release/version.json"
    if [ -f "$manifest" ] && grep -q "\"version\": \"${AGENT_VERSION}\"" "$manifest"; then
        log "检测到当前版本 Agent 发布包已存在: ${AGENT_VERSION}"
        return
    fi

    log "构建 Agent 发布包..."
    (cd "$PROJECT_ROOT/agent" && ./build-release.sh)
}

prepare_package_layout() {
    log "准备部署包目录..."
    rm -rf "$PACKAGE_DIR"
    mkdir -p "$PACKAGE_DIR/backend" \
        "$PACKAGE_DIR/frontend/dist" \
        "$PACKAGE_DIR/portal/site" \
        "$PACKAGE_DIR/agent/release" \
        "$PACKAGE_DIR/agent/installer-templates" \
        "$PACKAGE_DIR/templates"
}

copy_payload() {
    local server_jar
    server_jar="$(ls "$PROJECT_ROOT/server/target"/server-*.jar | head -1)"

    require_file "$server_jar"
    require_file "$PROJECT_ROOT/web/dist/index.html"
    require_file "$PROJECT_ROOT/agent/release/version.json"

    log "复制后端、前端、门户和 Agent 资源..."
    cp "$server_jar" "$PACKAGE_DIR/backend/server.jar"

    cp -R "$PROJECT_ROOT/web/dist/." "$PACKAGE_DIR/frontend/dist/"

    cp "$PROJECT_ROOT/portal/index.html" "$PACKAGE_DIR/portal/site/"
    cp "$PROJECT_ROOT/portal/client-install.html" "$PACKAGE_DIR/portal/site/"
    cp "$PROJECT_ROOT/portal/docs.html" "$PACKAGE_DIR/portal/site/"
    cp "$PROJECT_ROOT/portal/server-deploy.html" "$PACKAGE_DIR/portal/site/"
    cp "$PROJECT_ROOT/portal/script.js" "$PACKAGE_DIR/portal/site/"
    cp "$PROJECT_ROOT/portal/styles.css" "$PACKAGE_DIR/portal/site/"
    cp -R "$PROJECT_ROOT/portal/assets" "$PACKAGE_DIR/portal/site/"

    cp -R "$PROJECT_ROOT/agent/release/." "$PACKAGE_DIR/agent/release/"
    cp "$PROJECT_ROOT/agent/scripts/unix/install-linux.sh" "$PACKAGE_DIR/agent/installer-templates/install-linux.sh"
    cp "$PROJECT_ROOT/agent/scripts/unix/install-macos.sh" "$PACKAGE_DIR/agent/installer-templates/install-macos.sh"
    cp "$PROJECT_ROOT/agent/scripts/windows/install-agent.bat" "$PACKAGE_DIR/agent/installer-templates/install-windows.bat"

    cp "$PACKAGE_SOURCE_DIR/deploy.env.example" "$PACKAGE_DIR/deploy.env.example"
    cp "$PACKAGE_SOURCE_DIR/README.md" "$PACKAGE_DIR/README.md"
    cp "$PACKAGE_SOURCE_DIR/install-server.sh" "$PACKAGE_DIR/install-server.sh"
    cp "$PACKAGE_SOURCE_DIR/verify-deploy.sh" "$PACKAGE_DIR/verify-deploy.sh"
    cp "$PACKAGE_SOURCE_DIR/rollback.sh" "$PACKAGE_DIR/rollback.sh"
    cp "$PACKAGE_SOURCE_DIR/templates/application-prod.yml.template" "$PACKAGE_DIR/templates/"
    cp "$PACKAGE_SOURCE_DIR/templates/nginx-viberemote.conf.template" "$PACKAGE_DIR/templates/"
    cp "$PACKAGE_SOURCE_DIR/templates/portal-runtime-config.js.template" "$PACKAGE_DIR/templates/"
    chmod +x "$PACKAGE_DIR/install-server.sh" "$PACKAGE_DIR/verify-deploy.sh" "$PACKAGE_DIR/rollback.sh"
}

cleanup_package_artifacts() {
    find "$PACKAGE_DIR" \( -name '.DS_Store' -o -name '._*' \) -delete
}

write_metadata() {
    log "写入部署包元信息..."
    cat > "$PACKAGE_DIR/PACKAGE_INFO" <<EOF
PACKAGE_ID='${PACKAGE_ID}'
PACKAGE_BUILT_AT='$(date '+%Y-%m-%d %H:%M:%S')'
SERVER_VERSION='${SERVER_VERSION}'
AGENT_VERSION='${AGENT_VERSION}'
EOF
}

archive_package() {
    log "打包部署包..."
    mkdir -p "$OUTPUT_ROOT"
    COPYFILE_DISABLE=1 tar -czf "$PACKAGE_ARCHIVE" -C "$OUTPUT_ROOT" "$PACKAGE_ID"
}

publish_portal_downloads() {
    log "生成门户下载清单..."
    mkdir -p "$PORTAL_DOWNLOADS_DIR"
    cp "$PACKAGE_ARCHIVE" "$PORTAL_DOWNLOADS_DIR/"
    cp "$PACKAGE_ARCHIVE" "$LATEST_ARCHIVE"

    cat > "$MANIFEST_FILE" <<EOF
{
  "packageId": "${PACKAGE_ID}",
  "fileName": "$(basename "$PACKAGE_ARCHIVE")",
  "latestFileName": "$(basename "$LATEST_ARCHIVE")",
  "downloadUrl": "/downloads/$(basename "$LATEST_ARCHIVE")",
  "builtAt": "$(date '+%Y-%m-%d %H:%M:%S')",
  "serverVersion": "${SERVER_VERSION}",
  "agentVersion": "${AGENT_VERSION}"
}
EOF
}

main() {
    require_file "$PACKAGE_SOURCE_DIR/install-server.sh"
    require_file "$PACKAGE_SOURCE_DIR/templates/application-prod.yml.template"
    require_file "$PACKAGE_SOURCE_DIR/templates/nginx-viberemote.conf.template"
    require_file "$PACKAGE_SOURCE_DIR/templates/portal-runtime-config.js.template"

    build_server
    build_web
    ensure_agent_release
    prepare_package_layout
    copy_payload
    cleanup_package_artifacts
    write_metadata
    archive_package
    publish_portal_downloads

    log "部署包生成完成"
    echo ""
    echo -e "${GREEN}输出文件:${NC}"
    echo "  $PACKAGE_ARCHIVE"
    echo "  $LATEST_ARCHIVE"
    echo "  $MANIFEST_FILE"
}

main "$@"
