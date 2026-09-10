@echo off
chcp 65001 > nul
echo ===================================================
echo   Smart Parking Management System (OOP Full Stack)
echo ===================================================

if not exist bin mkdir bin

echo.
echo [1/3] Compiling Java Sources...
dir /s /b src\*.java > sources.txt
javac -encoding UTF-8 -d bin @sources.txt
if %errorlevel% neq 0 (
    echo [ERROR] Compilation failed!
    del sources.txt
    pause
    exit /b %errorlevel%
)
del sources.txt
echo [OK] Compilation successful!

echo.
echo [2/3] Running OOP Unit Tests...
java -cp bin test.ParkingSystemTest
if %errorlevel% neq 0 (
    echo [ERROR] Unit tests failed!
    pause
    exit /b %errorlevel%
)

echo.
echo [3/3] Starting Smart Parking System Web Server on http://localhost:8080 ...
start "" http://localhost:8080
java -cp bin server.ParkingServer

pause
