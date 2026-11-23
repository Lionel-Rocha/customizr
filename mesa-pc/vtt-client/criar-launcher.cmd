@echo off
setlocal EnableDelayedExpansion
rem ============================================================
rem  Customizr - cria o launcher (Customizr.exe) com dois cliques.
rem
rem  Gera a pasta "Customizr" ao lado de vtt-client, com o
rem  Customizr.exe (icone da fogueira, sem janela preta, com o
rem  Java ja embutido) e um atalho "Customizr" na pasta de cima.
rem  Opcional: atalho na area de trabalho.
rem
rem  Rode de novo sempre que atualizar o codigo (o .exe e uma
rem  "foto" do programa no momento em que foi criado).
rem  Suas mesas NAO ficam na pasta Customizr: pode apagar e
rem  recriar a vontade.
rem ============================================================

cd /d "%~dp0"
for %%I in ("%~dp0..") do set "OUT=%%~fI"

set "FOUND="
if defined JAVA_HOME call :check "%JAVA_HOME%"
if not defined FOUND for /d %%D in ("%USERPROFILE%\.jdks\*") do if not defined FOUND call :check "%%~fD"
if not defined FOUND for /d %%D in ("%ProgramFiles%\Java\*") do if not defined FOUND call :check "%%~fD"
if not defined FOUND for /d %%D in ("%ProgramFiles%\Eclipse Adoptium\*") do if not defined FOUND call :check "%%~fD"
if not defined FOUND for /d %%D in ("%ProgramFiles%\Microsoft\*") do if not defined FOUND call :check "%%~fD"
if not defined FOUND (
    echo [ERRO] Nao achei um JDK 25 ou mais novo ^(precisa ter o jpackage^).
    pause
    exit /b 1
)
set "JAVA_HOME=%FOUND%"
if not exist "%JAVA_HOME%\bin\jpackage.exe" (
    echo [ERRO] Esse JDK nao tem o jpackage: %JAVA_HOME%
    pause
    exit /b 1
)
echo Usando o JDK em: %JAVA_HOME%

echo.
echo [1/4] Compilando o Customizr...
call "%~dp0mvnw.cmd" -q -DskipTests package
if errorlevel 1 (
    echo [ERRO] A compilacao falhou. Veja as mensagens acima.
    pause
    exit /b 1
)

echo [2/4] Juntando os arquivos...
if exist "target\dist" rmdir /s /q "target\dist"
mkdir "target\dist\lib"
copy /y "target\customizr.jar" "target\dist\" >nul
copy /y "target\lib\*.jar" "target\dist\lib\" >nul

echo [3/4] Criando o Customizr.exe ^(leva um minutinho^)...
if exist "%OUT%\Customizr\Customizr.exe" rmdir /s /q "%OUT%\Customizr"
"%JAVA_HOME%\bin\jpackage.exe" --type app-image --name Customizr --dest "%OUT%" ^
    --input "target\dist" --main-jar customizr.jar --main-class com.vttcaseiro.app.Launcher ^
    --icon "src\main\resources\icons\customizr.ico" --app-version 1.0.0 --vendor Customizr ^
    --description "Customizr - mesa virtual de RPG" ^
    --jlink-options "--strip-debug --no-man-pages --no-header-files" ^
    --java-options "--enable-native-access=ALL-UNNAMED" --java-options "-Dprism.maxvram=2G"
if errorlevel 1 (
    echo [ERRO] O jpackage falhou. Veja as mensagens acima.
    pause
    exit /b 1
)

echo [4/4] Criando atalhos...
call :shortcut "%OUT%"
if /i "%~1"=="/semdesktop" goto done
choice /c SN /m "Criar atalho na area de trabalho tambem"
if errorlevel 2 goto done
for /f "usebackq delims=" %%D in (`powershell -NoProfile -Command "[Environment]::GetFolderPath('Desktop')"`) do set "DESK=%%D"
call :shortcut "%DESK%"

:done
echo.
echo Pronto. Abra o Customizr pelo atalho "Customizr" em:
echo     %OUT%
echo ^(ou direto: %OUT%\Customizr\Customizr.exe^)
pause
exit /b 0

rem ---- :shortcut <pasta> -> cria "Customizr.lnk" apontando pro .exe
:shortcut
powershell -NoProfile -Command "$s = New-Object -ComObject WScript.Shell; $l = $s.CreateShortcut((Join-Path '%~1' 'Customizr.lnk')); $l.TargetPath = '%OUT%\Customizr\Customizr.exe'; $l.WorkingDirectory = '%OUT%\Customizr'; $l.IconLocation = '%OUT%\Customizr\Customizr.exe,0'; $l.Description = 'Customizr - mesa virtual de RPG'; $l.Save()"
echo     atalho criado em %~1
exit /b 0

rem ---- :check <pasta> -> define FOUND se for um JDK 25+
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
