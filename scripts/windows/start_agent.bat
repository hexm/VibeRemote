@echo off
setlocal

set "JAVA17=C:\Program Files\Eclipse Adoptium\jdk-17.0.16.8-hotspot\bin\java.exe"
set "ROOT=%~dp0..\.."

if not exist "%JAVA17%" (
  echo JDK17 not found: %JAVA17%
  exit /b 1
)

if not exist "%ROOT%\agent\target\agent-0.1.0-SNAPSHOT-jar-with-dependencies.jar" (
  echo Agent jar not found at %ROOT%\agent\target\agent-0.1.0-SNAPSHOT-jar-with-dependencies.jar
  echo Please run from project root: mvn -q -DskipTests package
  exit /b 1
)

set "LS_SERVER=http://localhost:8080"
set "LS_REGISTER_TOKEN=dev-register-token"

"%JAVA17%" -jar "%ROOT%\agent\target\agent-0.1.0-SNAPSHOT-jar-with-dependencies.jar" 