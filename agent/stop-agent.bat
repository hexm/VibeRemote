@echo off
REM LightScript Agent 停止脚本 (Windows)

REM 切换到脚本目录
cd /d "%~dp0"

echo Stopping LightScript Agent...

setlocal enabledelayedexpansion
set STOPPED=false
set "LOCK_FILE=%USERPROFILE%\.lightscript\.agent.lock"

REM 方法1：通过锁文件查找进程
if exist "%LOCK_FILE%" (
    echo Found lock file, reading process info...
    
    REM 从锁文件读取PID信息
    for /f "tokens=2 delims=: " %%i in ('findstr "PID:" "%LOCK_FILE%" 2^>nul') do (
        for /f "tokens=1 delims=," %%j in ("%%i") do set PID=%%j
    )
    
    if defined PID (
        REM 检查进程是否存在
        tasklist /FI "PID eq !PID!" 2>nul | find /I "!PID!" >nul
        if not errorlevel 1 (
            echo Stopping Agent process ^(PID: !PID!^)...
            taskkill /PID !PID! /F >nul 2>&1
            
            REM 等待进程结束
            timeout /t 3 /nobreak >nul
            
            REM 检查进程是否已结束
            tasklist /FI "PID eq !PID!" 2>nul | find /I "!PID!" >nul
            if errorlevel 1 (
                echo Agent stopped successfully
                set STOPPED=true
            )
        )
    )
)

REM 方法2：通过进程名查找并停止
if "!STOPPED!"=="false" (
    echo Searching for Agent processes by name...
    
    REM 使用wmic查找包含agent.jar的java进程
    for /f "skip=1 tokens=2" %%i in ('wmic process where "name='java.exe' and commandline like '%%agent.jar%%'" get processid 2^>nul') do (
        if "%%i" neq "" (
            echo Stopping Agent process ^(PID: %%i^)...
            taskkill /PID %%i /F >nul 2>&1
            set STOPPED=true
        )
    )
    
    if "!STOPPED!"=="true" (
        echo All Agent processes stopped
        timeout /t 2 /nobreak >nul
    )
)

REM 方法3：备用进程查找
if "!STOPPED!"=="false" (
    echo Trying alternative process search...
    
    REM 查找java.exe进程并检查命令行
    for /f "tokens=1,2" %%a in ('tasklist /v /fo csv ^| findstr /i "java.exe"') do (
        set "PROCESS_NAME=%%a"
        set "PROCESS_PID=%%b"
        set PROCESS_NAME=!PROCESS_NAME:"=!
        set PROCESS_PID=!PROCESS_PID:"=!
        
        REM 获取进程命令行
        for /f "tokens=*" %%c in ('wmic process where "processid='!PROCESS_PID!'" get commandline /format:value 2^>nul ^| findstr "CommandLine"') do (
            echo %%c | findstr /i "agent.jar" >nul
            if not errorlevel 1 (
                echo Stopping Agent process ^(PID: !PROCESS_PID!^)...
                taskkill /PID !PROCESS_PID! /F >nul 2>&1
                set STOPPED=true
            )
        )
    )
)

REM 清理锁文件
if exist "%LOCK_FILE%" (
    echo Cleaning up lock file...
    del /f /q "%LOCK_FILE%" >nul 2>&1
)

if "!STOPPED!"=="false" (
    echo No Agent processes found
) else (
    echo Agent stopped. You can start it again with: start-agent.bat
)

endlocal