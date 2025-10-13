@echo off
setlocal ENABLEDELAYEDEXPANSION

REM Go to project root (this script location)
set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%"

echo ========================================
echo LightScript Server - Simple Start
echo ========================================
echo(

REM 1) Try to find built JAR (first match)
set "JAR_PATH="
for /f "delims=" %%F in ('dir /b /a:-d "server\target\server-*.jar" 2^>nul') do (
  set "JAR_PATH=server\target\%%F"
  goto :FOUND_JAR
)

REM 2) If not found, try to build (requires Maven in PATH)
echo [INFO] No JAR found. Building with Maven (mvn -q -f server\pom.xml clean package -DskipTests)
mvn -q -f server\pom.xml clean package -DskipTests
if errorlevel 1 (
  echo [ERROR] Build failed. Ensure Maven is installed and in PATH (check with: mvn -v).
  pause
  goto :END
)

:FOUND_JAR
echo [INFO] Using JAR: %JAR_PATH%
echo [INFO] Start URL: http://localhost:8080 (H2 in-memory DB)
echo [INFO] Default login: admin / admin123
echo.

REM 3) Start with H2 overrides to avoid local MySQL dependency
echo [INFO] Starting server with optimized settings...
java -Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Xmx512m -Xms256m ^
     -jar "%JAR_PATH%" ^
     --server.port=8080 ^
     --spring.datasource.url=jdbc:h2:mem:lightscript ^
     --spring.datasource.driver-class-name=org.h2.Driver ^
     --spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect ^
     --spring.jpa.hibernate.ddl-auto=update ^
     --spring.jpa.show-sql=false ^
     --logging.level.org.hibernate.SQL=OFF ^
     --logging.level.org.hibernate.type.descriptor.sql.BasicBinder=OFF ^
     --logging.level.org.hibernate.type=OFF ^
     --logging.level.org.hibernate=WARN ^
     --logging.level.root=INFO

:END
echo.
echo [INFO] Server stopped.
echo ========================================
pause
endlocal
