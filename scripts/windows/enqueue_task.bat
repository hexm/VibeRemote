@echo off
setlocal

rem Replace with values printed by start_agent.bat
set "AGENT_ID=c6ce28f6-738d-4f1c-8318-d1ff82acbe58"
set "AGENT_TOKEN=a911374e-060d-40b8-9518-b63ec04d04dd"

set "PAYLOAD_FILE=%~dp0payload.json"
> "%PAYLOAD_FILE%" (
  echo {^
  echo   "scriptLang":"powershell",^
  echo   "scriptContent":"Write-Output ^\"hello^\"; Start-Sleep -Seconds 1; Write-Output ^\"world^\"",^
  echo   "timeoutSec":30^
  echo }
)

for /f "usebackq tokens=2 delims=: " %%A in (`curl.exe -s -H "Content-Type: application/json" -X POST --data "@%PAYLOAD_FILE%" "http://localhost:8080/api/agent/debug/enqueue?agentId=%AGENT_ID%^&agentToken=%AGENT_TOKEN%" ^| findstr /i "taskId"`) do set "TASK_ID=%%~A"
set "TASK_ID=%TASK_ID:,=%"
set "TASK_ID=%TASK_ID:\"=%"
set "TASK_ID=%TASK_ID: =%"

echo TASK_ID=%TASK_ID%
del /q "%PAYLOAD_FILE%" >nul 2>nul 