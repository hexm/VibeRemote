@echo off
REM Simple front-end starter at project root. Serves web/ folder on http://localhost:3000
setlocal

set "SCRIPT_DIR=%~dp0"
set "WEB_DIR=%SCRIPT_DIR%web"

echo ========================================
echo LightScript Web - Simple Start
echo ========================================
echo(

if not exist "%WEB_DIR%" (
  echo [ERROR] web directory not found: %WEB_DIR%
  goto :END
)

REM Prefer Node http-server if available
where http-server >nul 2>&1
if %ERRORLEVEL% EQU 0 (
  echo [INFO] Starting with Node http-server on http://localhost:3000
  pushd "%WEB_DIR%" >nul
  http-server . -p 3000 -c-1 --cors
  popd >nul
  goto :END
)

REM Fallback to Python if available
where python >nul 2>&1
if %ERRORLEVEL% EQU 0 (
  echo [INFO] Starting with Python http.server on http://localhost:3000
  pushd "%WEB_DIR%" >nul
  python -m http.server 3000
  popd >nul
  goto :END
)

echo [ERROR] Neither http-server nor python found in PATH.
echo         Install Node (and http-server) or Python, or open %WEB_DIR%\index.html directly in browser.

:END
echo(
echo ========================================
endlocal
