@echo off
setlocal EnableDelayedExpansion
rem ============================================================
rem  Customizr - cria a pasta "Customizr-Ponte" (ao lado de vtt-client)
rem  com o customizr-ponte.jar e os arquivos pra rodar num servidor.
rem  Leia ponte\LEIA-ME.md.
rem ============================================================
cd /d "%~dp0"
for %%I in ("%~dp0..") do set "OUT=%%~fI\Customizr-Ponte"

set "FOUND="
if defined JAVA_HOME call :check "%JAVA_HOME%"
if not defined FOUND for /d %%D in ("%USERPROFILE%\.jdks\*") do if not defined FOUND call :check "%%~fD"
if not defined FOUND for /d %%D in ("%ProgramFiles%\Java\*") do if not defined FOUND call :check "%%~fD"
if not defined FOUND for /d %%D in ("%ProgramFiles%\Eclipse Adoptium\*") do if not defined FOUND call :check "%%~fD"
if not defined FOUND (
    echo [ERRO] Nao achei um JDK 25 ou mais novo.
    pause
    exit /b 1
)
set "JAVA_HOME=%FOUND%"

echo [1/2] Compilando...
call "%~dp0mvnw.cmd" -q -DskipTests compile
if errorlevel 1 (
    echo [ERRO] A compilacao falhou.
    pause
    exit /b 1
)

echo [2/2] Montando a pasta da ponte...
if exist "%OUT%" rmdir /s /q "%OUT%"
mkdir "%OUT%"
"%JAVA_HOME%\bin\jar.exe" --create --file "%OUT%\customizr-ponte.jar" --main-class com.vttcaseiro.relay.RelayServer -C target\classes com\vttcaseiro\relay
if errorlevel 1 (
    echo [ERRO] Nao consegui criar o jar.
    pause
    exit /b 1
)
copy /y "ponte\*" "%OUT%\" >nul

echo.
echo Pronto. Pasta da ponte: %OUT%
echo Copie essa pasta pro servidor e siga o LEIA-ME.md que esta dentro dela.
echo ^(Pra testar aqui mesmo: rodar-ponte.cmd dentro da pasta.^)
pause
exit /b 0

:check
set "CANDIDATE=%~1"
if not exist "%CANDIDATE%\bin\jar.exe" exit /b 0
if not exist "%CANDIDATE%\release" exit /b 0
set "VER="
for /f "tokens=2 delims==" %%V in ('findstr /b "JAVA_VERSION=" "%CANDIDATE%\release"') do set "VER=%%~V"
if not defined VER exit /b 0
for /f "tokens=1 delims=." %%M in ("%VER%") do set "MAJOR=%%M"
if !MAJOR! GEQ 25 set "FOUND=%CANDIDATE%"
exit /b 0
