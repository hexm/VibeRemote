#!/bin/bash

# LightScript 门户网站部署脚本

set -e

echo "🚀 开始部署 LightScript 门户网站..."

# 检查必要文件
required_files=("index.html" "styles.css" "script.js" "assets/logo.svg")
for file in "${required_files[@]}"; do
    if [ ! -f "$file" ]; then
        echo "❌ 缺少必要文件: $file"
        exit 1
    fi
done

echo "✅ 文件检查完成"

# 创建构建目录
BUILD_DIR="dist"
rm -rf $BUILD_DIR
mkdir -p $BUILD_DIR
mkdir -p $BUILD_DIR/assets

echo "📁 创建构建目录: $BUILD_DIR"

# 复制文件
cp index.html $BUILD_DIR/
cp styles.css $BUILD_DIR/
cp script.js $BUILD_DIR/
cp -r assets/* $BUILD_DIR/assets/

echo "📋 文件复制完成"

# 压缩 CSS 和 JS（如果有相关工具）
if command -v uglifyjs &> /dev/null; then
    echo "🗜️  压缩 JavaScript..."
    uglifyjs script.js -o $BUILD_DIR/script.min.js
    # 更新 HTML 中的引用
    sed -i 's/script.js/script.min.js/g' $BUILD_DIR/index.html
fi

if command -v cleancss &> /dev/null; then
    echo "🗜️  压缩 CSS..."
    cleancss styles.css -o $BUILD_DIR/styles.min.css
    # 更新 HTML 中的引用
    sed -i 's/styles.css/styles.min.css/g' $BUILD_DIR/index.html
fi

# 生成版本信息
echo "📝 生成版本信息..."
cat > $BUILD_DIR/version.json << EOF
{
    "version": "1.0.0",
    "buildTime": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")",
    "commit": "$(git rev-parse --short HEAD 2>/dev/null || echo 'unknown')"
}
EOF

# 创建 .htaccess 文件（Apache 服务器）
cat > $BUILD_DIR/.htaccess << 'EOF'
# 启用 Gzip 压缩
<IfModule mod_deflate.c>
    AddOutputFilterByType DEFLATE text/plain
    AddOutputFilterByType DEFLATE text/html
    AddOutputFilterByType DEFLATE text/xml
    AddOutputFilterByType DEFLATE text/css
    AddOutputFilterByType DEFLATE application/xml
    AddOutputFilterByType DEFLATE application/xhtml+xml
    AddOutputFilterByType DEFLATE application/rss+xml
    AddOutputFilterByType DEFLATE application/javascript
    AddOutputFilterByType DEFLATE application/x-javascript
</IfModule>

# 设置缓存
<IfModule mod_expires.c>
    ExpiresActive on
    ExpiresByType text/css "access plus 1 year"
    ExpiresByType application/javascript "access plus 1 year"
    ExpiresByType image/png "access plus 1 year"
    ExpiresByType image/svg+xml "access plus 1 year"
</IfModule>

# 安全头
<IfModule mod_headers.c>
    Header always set X-Content-Type-Options nosniff
    Header always set X-Frame-Options DENY
    Header always set X-XSS-Protection "1; mode=block"
</IfModule>
EOF

# 创建 nginx.conf 示例
cat > $BUILD_DIR/nginx.conf.example << 'EOF'
server {
    listen 80;
    server_name lightscript.example.com;
    root /var/www/lightscript-portal;
    index index.html;

    # Gzip 压缩
    gzip on;
    gzip_types text/plain text/css application/javascript image/svg+xml;

    # 缓存设置
    location ~* \.(css|js|png|svg)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }

    # 安全头
    add_header X-Content-Type-Options nosniff;
    add_header X-Frame-Options DENY;
    add_header X-XSS-Protection "1; mode=block";

    # 主页面
    location / {
        try_files $uri $uri/ /index.html;
    }
}
EOF

echo "⚙️  配置文件生成完成"

# 验证 HTML
if command -v tidy &> /dev/null; then
    echo "🔍 验证 HTML..."
    tidy -q -e $BUILD_DIR/index.html || echo "⚠️  HTML 验证发现警告"
fi

# 计算文件大小
echo "📊 构建统计:"
echo "   HTML: $(du -h $BUILD_DIR/index.html | cut -f1)"
echo "   CSS:  $(du -h $BUILD_DIR/styles*.css | cut -f1)"
echo "   JS:   $(du -h $BUILD_DIR/script*.js | cut -f1)"
echo "   总计: $(du -sh $BUILD_DIR | cut -f1)"

echo ""
echo "✅ 部署完成！"
echo "📁 构建文件位于: $BUILD_DIR"
echo ""
echo "🌐 部署选项:"
echo "   1. 静态服务器: python -m http.server 8000 -d $BUILD_DIR"
echo "   2. Nginx: 复制文件到 /var/www/html/"
echo "   3. Apache: 复制文件到 DocumentRoot"
echo "   4. CDN: 上传 $BUILD_DIR 目录到 CDN"
echo ""
echo "🔗 本地预览: http://localhost:8000"