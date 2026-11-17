@echo off
setlocal EnableDelayedExpansion
rem ============================================================
rem  Customizr - abre o programa com dois cliques.
rem
rem  Precisa de um JDK 25 ou mais novo. Procura nesta ordem:
rem    1. JAVA_HOME (se ja apontar pra um JDK 25+)
rem    2. %USERPROFILE%\.jdks\*   (JDKs baixados pelo IntelliJ)
rem    3. C:\Program Files\Java\*, Eclipse Adoptium, Microsoft
rem  O Maven NAO precisa estar instalado: o mvnw.cmd baixa sozinho
rem  na primeira vez (precisa de internet so nessa primeira vez).
rem
rem  Uso:  rodar.cmd          abre o VTT
rem        rodar.cmd test     roda os testes
rem ============================================================

cd /d "%~dp0"

set "FOUND="
if defined JAVA_HOME call :check "%JAVA_HOME%"
if not defined FOUND for /d %%D in ("%USERPROFILE%\.jdks\*") do if not defined FOUND call :check "%%~fD"
if not defined FOUND for /d %%D in ("%ProgramFiles%\Java\*") do if not defined FOUND call :check "%%~fD"
if not defined FOUND for /d %%D in ("%ProgramFiles%\Eclipse Adoptium\*") do if not defined FOUND call :check "%%~fD"
if not defined FOUND for /d %%D in ("%ProgramFiles%\Microsoft\*") do if not defined FOUND call :check "%%~fD"

if not defined FOUND (
    echo.
    echo [ERRO] Nao achei um JDK 25 ou mais novo nesta maquina.
    echo        O "java" padrao do Windows aqui pode ser o Java 8, que nao serve.
    echo        Instale um JDK 25+ ^(ex.: https://adoptium.net^) ou, no IntelliJ:
    echo        File ^> Project Structure ^> SDK ^> Download JDK ^(versao 25 ou mais^).
    echo.
    pause
    exit /b 1
)

set "JAVA_HOME=%FOUND%"
echo Usando o JDK em: %JAVA_HOME%

if /i "%~1"=="test" (
    call "%~dp0mvnw.cmd" -q test
) else (
    call "%~dp0mvnw.cmd" -q javafx:run
)
set "RESULT=%ERRORLEVEL%"
if not "%RESULT%"=="0" (
    echo.
    echo [ERRO] O programa terminou com erro ^(codigo %RESULT%^). Veja as mensagens acima.
    pause
)
exit /b %RESULT%

rem ---- :check <pasta> -> define FOUND se for um JDK 25+ (tem javac e versao >= 25)
:check
set "CANDIDATE=%~1"
if not exist "%CANDIDATE%\bin\javac.exe" exit /b 0
if not exist "%CANDIDATE%\release" exit /b 0
set "VER="
for /f "tokens=2 delims==" %%V in ('findstr /b "JAVA_VERSION=" "%CANDIDATE%\release"') do set "VER=%%~V"
if not defined VER exit /b 0
for /f "tokens=1 delims=." %%M in ("%VER%") do set "MAJOR=%%M"
if !MAJOR! GEQ 25 set "FOUND=%CANDIDATE%"
exit /b 0
