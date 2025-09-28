@echo off
setlocal

set "JAVA17=C:\Program Files\Eclipse Adoptium\jdk-17.0.16.8-hotspot\bin\java.exe"
set "ROOT=%~dp0..\.."

if not exist "%JAVA17%" (
  echo JDK17 not found: %JAVA17%
  exit /b 1
)

if not exist "%ROOT%\server\target\server-0.1.0-SNAPSHOT.jar" (
  echo Server jar not found at %ROOT%\server\target\server-0.1.0-SNAPSHOT.jar
  echo Please run from project root: mvn -q -DskipTests package
  exit /b 1
)

"%JAVA17%" -jar "%ROOT%\server\target\server-0.1.0-SNAPSHOT.jar" 