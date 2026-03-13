#!/bin/bash

# 获取脚本所在目录的父目录（项目根目录）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

echo "========================================"
echo "LightScript Server - Simple Start"
echo "========================================"
echo

# 1) 尝试找到已构建的 JAR 文件
JAR_PATH=""
if ls server/target/server-*.jar 1> /dev/null 2>&1; then
    JAR_PATH=$(ls server/target/server-*.jar | head -n 1)
    echo "[INFO] 找到 JAR 文件: $JAR_PATH"
else
    # 2) 如果没找到，尝试构建
    echo "[INFO] 未找到 JAR 文件，正在使用 Maven 构建..."
    
    if ! command -v mvn &> /dev/null; then
        echo "[ERROR] Maven 未安装。请安装 Maven (brew install maven) 或手动构建项目。"
        exit 1
    fi
    
    mvn -q -f server/pom.xml clean package -DskipTests
    if [ $? -ne 0 ]; then
        echo "[ERROR] 构建失败。请确保 Maven 已安装并在 PATH 中 (检查: mvn -v)。"
        exit 1
    fi
    
    JAR_PATH=$(ls server/target/server-*.jar | head -n 1)
fi

echo "[INFO] 使用 JAR: $JAR_PATH"
echo "[INFO] 启动 URL: http://localhost:8080 (H2 持久化数据库)"
echo "[INFO] 默认登录: admin / admin123"
echo

# 3) 使用 H2 持久化数据库启动
echo "[INFO] 正在启动服务器（使用持久化数据库）..."
java -Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Xmx512m -Xms256m \
     -jar "$JAR_PATH" \
     --server.port=8080 \
     --spring.datasource.url=jdbc:h2:./data/lightscript \
     --spring.datasource.driver-class-name=org.h2.Driver \
     --spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect \
     --spring.jpa.hibernate.ddl-auto=update \
     --spring.jpa.show-sql=false \
     --logging.level.org.hibernate.SQL=OFF \
     --logging.level.org.hibernate.type.descriptor.sql.BasicBinder=OFF \
     --logging.level.org.hibernate.type=OFF \
     --logging.level.org.hibernate=WARN \
     --logging.level.root=INFO

echo
echo "[INFO] 服务器已停止。"
echo "========================================"