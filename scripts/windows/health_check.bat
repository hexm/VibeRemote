@echo off
setlocal

curl.exe -s http://localhost:8080/actuator/health
echo. 