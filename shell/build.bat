@echo off
REM ============================================================
REM AgentDSL — 编译打包脚本 (Windows)
REM 用法: bin\build.bat
REM ============================================================

setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
set "PROJECT_ROOT=%SCRIPT_DIR%.."

echo ===================================
echo  AgentDSL — 开始编译打包 ...
echo  项目根目录: %PROJECT_ROOT%
echo ===================================
echo.

cd /d "%PROJECT_ROOT%"

REM 执行 Gradle 构建
echo [INFO] 执行 Gradle 构建 (clean + shadowJar) ...
call gradlew.bat clean :agentdsl-cli:shadowJar --warning-mode all

if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] Gradle 构建失败！
    exit /b 1
)

set "JAR_FILE=%PROJECT_ROOT%\agentdsl-cli\build\libs\agentdsl.jar"

if exist "%JAR_FILE%" (
    echo.
    echo [SUCCESS] 构建成功！
    echo    产物路径: %JAR_FILE%
    echo.
    echo [TIP] 使用以下命令运行 CLI:
    echo    java -jar %JAR_FILE% --help
    echo    或使用: bin\agentdsl.bat run examples\simple-chat.agent.groovy --chat "你好"
) else (
    echo.
    echo [ERROR] 构建失败：未生成 agentdsl.jar
    exit /b 1
)

endlocal
