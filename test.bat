@echo off
chcp 65001 > nul
echo ===================================================
echo   Running Parking Management System Unit Tests
echo ===================================================

if not exist bin mkdir bin

dir /s /b src\*.java > sources.txt
javac -encoding UTF-8 -d bin @sources.txt
del sources.txt

java -cp bin test.ParkingSystemTest
pause
