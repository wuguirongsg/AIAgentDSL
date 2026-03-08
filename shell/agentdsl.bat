@echo off
REM ============================================================
REM AgentDSL — CLI 运行入口脚本 (Windows)
REM 用法:
REM   bin\agentdsl.bat run examples\simple-chat.agent.groovy --chat "你好"
REM   bin\agentdsl.bat validate examples\simple-chat.agent.groovy
REM   bin\agentdsl.bat list examples\simple-chat.agent.groovy
REM   bin\agentdsl.bat --help
REM ============================================================

setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
set "PROJECT_ROOT=%SCRIPT_DIR%.."
set "JAR_FILE=%PROJECT_ROOT%\agentdsl-cli\build\libs\agentdsl.jar"

REM 如果 JAR 不存在，自动触发构建
if not exist "%JAR_FILE%" (
    echo [INFO] 未检测到构建产物，正在自动编译 ...
    call "%SCRIPT_DIR%build.bat"
    echo.
)

REM 可通过 JAVA_OPTS 环境变量传入额外 JVM 参数
if not defined JAVA_OPTS set "JAVA_OPTS="

java %JAVA_OPTS% -jar "%JAR_FILE%" %*

endlocal
