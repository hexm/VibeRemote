# LightScript 手动安装指南

由于网络问题导致 Homebrew 安装失败，这里提供手动安装的详细步骤。

## 🎯 需要安装的软件

1. **Java JDK 8+** - 运行 Spring Boot 和 Agent
2. **Apache Maven** - 构建项目
3. **Python 3** (可选) - 前端服务器

## 📥 方案一：直接下载安装包

### 1. 安装 Java JDK

#### 选项 A：Oracle JDK (需要注册)
1. 访问：https://www.oracle.com/java/technologies/javase/javase8-archive-downloads.html
2. 下载 `jdk-8u XXX-macosx-x64.dmg`
3. 双击 `.dmg` 文件安装

#### 选项 B：OpenJDK (推荐，免费)
1. 访问：https://adoptium.net/temurin/releases/
2. 选择：
   - Version: 8 (LTS)
   - Operating System: macOS
   - Architecture: x64 (Intel) 或 aarch64 (Apple Silicon)
3. 下载 `.pkg` 文件
4. 双击安装

#### 选项 C：Amazon Corretto (免费)
1. 访问：https://aws.amazon.com/corretto/
2. 选择 Corretto 8
3. 下载 macOS 版本
4. 双击安装

### 2. 安装 Maven

1. 访问：https://maven.apache.org/download.cgi
2. 下载 `apache-maven-3.9.6-bin.tar.gz`
3. 解压到用户目录：
   ```bash
   cd ~/Downloads
   tar -xzf apache-maven-3.9.6-bin.tar.gz
   sudo mv apache-maven-3.9.6 /usr/local/maven
   ```

### 3. 设置环境变量

编辑 shell 配置文件：
```bash
# 编辑 .zshrc 文件
nano ~/.zshrc

# 添加以下内容：
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home
export MAVEN_HOME=/usr/local/maven
export PATH=$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH

# 保存并重新加载
source ~/.zshrc
```

### 4. 验证安装

```bash
java -version
mvn -version
```

## 📥 方案二：使用便携式环境

如果不想修改系统环境，可以使用我们创建的便携式环境：

```bash
# 1. 运行便携式环境设置
chmod +x scripts/mac/setup-portable-env.sh
./scripts/mac/setup-portable-env.sh

# 2. 按提示手动下载 Java（如果需要）

# 3. 设置环境变量
source ./set-env.sh

# 4. 启动项目
./scripts/mac/start-portable.sh
```

## 🚀 快速验证和启动

安装完成后，验证环境：

```bash
# 检查 Java
java -version
# 应该显示类似：openjdk version "1.8.0_XXX"

# 检查 Maven  
mvn -version
# 应该显示：Apache Maven 3.x.x

# 进入项目目录
cd LightScript

# 构建项目
mvn clean package -DskipTests

# 启动服务器
cd server
java -jar target/server-*.jar --spring.profiles.active=dev
```

## 🌐 前端服务（可选）

### 使用 Python（系统自带）
```bash
# 进入 web 目录
cd web

# 启动 HTTP 服务器
python3 -m http.server 3000
```

### 或者直接打开文件
```bash
# 直接在浏览器中打开
open web/index.html
```

## 🔧 常见问题解决

### Java 版本问题
```bash
# 查看所有已安装的 Java 版本
/usr/libexec/java_home -V

# 设置特定版本
export JAVA_HOME=$(/usr/libexec/java_home -v 1.8)
```

### Maven 权限问题
```bash
# 如果遇到权限问题，使用 sudo
sudo mv apache-maven-3.9.6 /usr/local/maven
sudo chown -R $(whoami) /usr/local/maven
```

### 端口被占用
```bash
# 查看端口占用
lsof -i :8080

# 杀死占用进程
kill -9 <PID>
```

## 📋 安装检查清单

- [ ] Java JDK 8+ 已安装
- [ ] Maven 3.6+ 已安装
- [ ] 环境变量已设置
- [ ] `java -version` 命令正常
- [ ] `mvn -version` 命令正常
- [ ] 项目构建成功
- [ ] 服务器启动成功
- [ ] 可以访问 http://localhost:8080

## 🎉 完成后的下一步

1. **访问系统**：http://localhost:8080
2. **默认账号**：admin / admin123
3. **启动客户端**：运行 `./scripts/mac/start-agent.sh`
4. **查看文档**：阅读 `README.md` 了解更多功能

## 💡 小贴士

- 如果网络较慢，可以使用国内镜像源
- 建议使用 OpenJDK 而不是 Oracle JDK（免费且功能相同）
- 可以同时安装多个 Java 版本，通过 JAVA_HOME 切换
- Maven 第一次运行会下载依赖，需要一些时间

这种手动安装方式虽然步骤多一些，但更加可控，不依赖网络状况。