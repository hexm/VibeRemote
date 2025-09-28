@echo off
echo ========================================
echo LightScript Web 前端启动脚本
echo ========================================
echo.

REM 检查是否安装了 Node.js
node --version >nul 2>&1
if errorlevel 1 (
    echo 警告: 未检测到 Node.js，将使用 Python 启动 HTTP 服务器
    goto :python_server
)

REM 检查是否安装了 http-server
npm list -g http-server >nul 2>&1
if errorlevel 1 (
    echo 正在安装 http-server...
    npm install -g http-server
)

echo 使用 Node.js http-server 启动前端服务...
echo 访问地址: http://localhost:3000
echo 按 Ctrl+C 停止服务
echo ========================================
http-server . -p 3000 -c-1 --cors
goto :end

:python_server
REM 检查是否安装了 Python
python --version >nul 2>&1
if errorlevel 1 (
    echo 错误: 未找到 Node.js 或 Python，无法启动 HTTP 服务器
    echo 请安装 Node.js 或 Python，或直接用浏览器打开 index.html
    pause
    exit /b 1
)

echo 使用 Python 启动 HTTP 服务器...
echo 访问地址: http://localhost:3000
echo 按 Ctrl+C 停止服务
echo ========================================
python -m http.server 3000

:end
pause
