@echo off
rem Ponte do Customizr (Windows). Precisa de Java 25 ou mais novo.
rem Uso: rodar-ponte.cmd [porta]   (padrao 55070)
cd /d "%~dp0"
set "PORTA=%~1"
if "%PORTA%"=="" set "PORTA=55070"
java -Xmx128m -jar customizr-ponte.jar %PORTA%
pause
