@echo off
chcp 65001 >nul
echo ========================================
echo Reset Agent ID
echo ========================================
echo.
echo This script will delete the saved Agent ID.
echo Next time the agent starts, it will register as a new agent.
echo.
echo Saved Agent ID file location:
echo %USERPROFILE%\.lightscript\.agent_id
echo.

pause

if exist "%USERPROFILE%\.lightscript\.agent_id" (
    del "%USERPROFILE%\.lightscript\.agent_id"
    echo.
    echo ✓ Agent ID file deleted successfully!
    echo.
    echo Next time you start the agent, it will register as a new agent.
) else (
    echo.
    echo ℹ No saved Agent ID file found.
)

echo.
pause
