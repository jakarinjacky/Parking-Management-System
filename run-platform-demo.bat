@echo off
setlocal
chcp 65001 > nul
cd /d "%~dp0"
echo DEMO ONLY - do not expose these accounts to the public internet.
set "PLATFORM_DEMO=true"
set "PLATFORM_DATA_DIR=data/platform-demo"
if not exist bin mkdir bin
dir /s /b src\*.java > platform-sources.txt
javac -encoding UTF-8 -d bin @platform-sources.txt
if errorlevel 1 (
  echo Install JDK 17 or newer and make javac available on PATH.
  pause
  exit /b 1
)
del platform-sources.txt
echo Open http://localhost:8080/platform.html after the server starts.
echo Demo users: superadmin, owner, owner2, admin, staff
echo Demo password: DemoPass123!
java -cp bin server.ParkingServer
pause
