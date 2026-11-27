@echo off
setlocal EnableDelayedExpansion
rem ============================================================
rem  Customizr - cria o INSTALADOR (Customizr-1.0.N.exe).
rem
rem  Quem recebe so da dois cliques e "Avancar": instala na pasta
rem  do usuario (nao pede senha de administrador), cria atalho no
rem  menu Iniciar e (se quiser) na area de trabalho, aparece em
rem  "Adicionar ou remover programas" e uma versao nova instala
rem  por cima da antiga. As mesas de cada um NAO ficam na pasta do
rem  programa: desinstalar nao apaga nenhuma mesa.
rem
rem  Precisa do JDK 25+ (com jpackage) e do WiX Toolset 3.14
rem  (D:\tools\wix314, ou instalado, ou no PATH).
rem  Sai na pasta "instalador" ao lado de vtt-client.
rem ============================================================

cd /d "%~dp0"
for %%I in ("%~dp0..") do set "OUT=%%~fI\instalador"

set "FOUND="
if defined JAVA_HOME call :check "%JAVA_HOME%"
if not defined FOUND for /d %%D in ("%USERPROFILE%\.jdks\*") do if not defined FOUND call :check "%%~fD"
if not defined FOUND for /d %%D in ("%ProgramFiles%\Java\*") do if not defined FOUND call :check "%%~fD"
if not defined FOUND for /d %%D in ("%ProgramFiles%\Eclipse Adoptium\*") do if not defined FOUND call :check "%%~fD"
if not defined FOUND for /d %%D in ("%ProgramFiles%\Microsoft\*") do if not defined FOUND call :check "%%~fD"
if not defined FOUND (
    echo [ERRO] Nao achei um JDK 25 ou mais novo ^(precisa ter o jpackage^).
    if /i not "%~1"=="/semparar" pause
    exit /b 1
)
set "JAVA_HOME=%FOUND%"
echo Usando o JDK em: %JAVA_HOME%

rem ---- WiX (o jpackage usa o candle.exe e o light.exe pra montar o instalador)
where candle.exe >nul 2>nul
if errorlevel 1 (
    if exist "D:\tools\wix314\candle.exe" set "PATH=D:\tools\wix314;!PATH!"
    if exist "%ProgramFiles(x86)%\WiX Toolset v3.14\bin\candle.exe" set "PATH=%ProgramFiles(x86)%\WiX Toolset v3.14\bin;!PATH!"
    if defined WIX if exist "%WIX%bin\candle.exe" set "PATH=%WIX%bin;!PATH!"
)
where candle.exe >nul 2>nul
if errorlevel 1 (
    echo [ERRO] Nao achei o WiX Toolset 3.14 ^(candle.exe^). Baixe em github.com/wixtoolset/wix3/releases
    echo        e descompacte em D:\tools\wix314.
    if /i not "%~1"=="/semparar" pause
    exit /b 1
)

rem ---- versao: 1.0.N, N = quantas mudancas o programa ja teve (sempre cresce: o instalador novo substitui o velho)
set "BUILD=0"
for /f %%C in ('git -C "%~dp0." rev-list --count HEAD -- . 2^>nul') do set "BUILD=%%C"
set "VERSION=1.0.%BUILD%"
echo Versao do instalador: %VERSION%

echo.
echo [1/3] Compilando o Customizr...
call "%~dp0mvnw.cmd" -q -DskipTests package
if errorlevel 1 (
    echo [ERRO] A compilacao falhou. Veja as mensagens acima.
    if /i not "%~1"=="/semparar" pause
    exit /b 1
)

echo [2/3] Juntando os arquivos...
if exist "target\dist" rmdir /s /q "target\dist"
mkdir "target\dist\lib"
copy /y "target\customizr.jar" "target\dist\" >nul
copy /y "target\lib\*.jar" "target\dist\lib\" >nul
git -C "%~dp0." log -1 --format="%%H %%cI" -- . > "target\dist\versao.txt" 2>nul

echo [3/3] Criando o instalador ^(leva alguns minutos^)...
if not exist "%OUT%" mkdir "%OUT%"
if exist "%OUT%\Customizr-%VERSION%.exe" del /q "%OUT%\Customizr-%VERSION%.exe"
rem upgrade-uuid FIXO: e por ele que o Windows sabe que a versao nova substitui a antiga (nunca troque)
"%JAVA_HOME%\bin\jpackage.exe" --type exe --name Customizr --dest "%OUT%" ^
    --input "target\dist" --main-jar customizr.jar --main-class com.vttcaseiro.app.Launcher ^
    --icon "src\main\resources\icons\customizr.ico" --app-version %VERSION% --vendor Customizr ^
    --description "Customizr - mesa virtual de RPG" ^
    --jlink-options "--strip-debug --no-man-pages --no-header-files" ^
    --java-options "--enable-native-access=ALL-UNNAMED" --java-options "-Dprism.maxvram=2G" ^
    --win-per-user-install --win-dir-chooser --win-menu --win-menu-group Customizr ^
    --win-shortcut --win-shortcut-prompt ^
    --win-upgrade-uuid 79ec7380-1563-454a-8504-48ad68f64ec5
if errorlevel 1 (
    echo [ERRO] O jpackage falhou. Veja as mensagens acima.
    if /i not "%~1"=="/semparar" pause
    exit /b 1
)

echo.
echo Pronto: %OUT%\Customizr-%VERSION%.exe
echo Mande esse arquivo pra quem vai jogar. Dois cliques e "Avancar".
if /i not "%~1"=="/semparar" pause
exit /b 0

rem ---- :check <pasta> -> define FOUND se for um JDK 25+
:check
set "CANDIDATE=%~1"
if not exist "%CANDIDATE%\bin\javac.exe" exit /b 0
if not exist "%CANDIDATE%\release" exit /b 0
if not exist "%CANDIDATE%\bin\jpackage.exe" exit /b 0
set "VER="
for /f "tokens=2 delims==" %%V in ('findstr /b "JAVA_VERSION=" "%CANDIDATE%\release"') do set "VER=%%~V"
if not defined VER exit /b 0
for /f "tokens=1 delims=." %%M in ("%VER%") do set "MAJOR=%%M"
if !MAJOR! GEQ 25 set "FOUND=%CANDIDATE%"
exit /b 0
