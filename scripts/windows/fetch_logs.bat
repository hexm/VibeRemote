@echo off
setlocal

rem Replace with the TASK_ID printed by enqueue_task.bat
set "TASK_ID=REPLACE_WITH_TASK_ID"

curl.exe -s "http://localhost:8080/api/agent/tasks/%TASK_ID%/logs"
echo. 