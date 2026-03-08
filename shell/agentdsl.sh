#!/usr/bin/env bash
# ============================================================
# AgentDSL — CLI 运行入口脚本 (Mac / Linux)
# 用法:
#   ./bin/agentdsl.sh run examples/simple-chat.agent.groovy --chat "你好"
#   ./bin/agentdsl.sh validate examples/simple-chat.agent.groovy
#   ./bin/agentdsl.sh list examples/simple-chat.agent.groovy
#   ./bin/agentdsl.sh --help
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
JAR_FILE="$PROJECT_ROOT/agentdsl-cli/build/libs/agentdsl.jar"

# 如果 JAR 不存在，自动触发构建
if [ ! -f "$JAR_FILE" ]; then
    echo "⏳ 未检测到构建产物，正在自动编译 ..."
    "$SCRIPT_DIR/build.sh"
    echo ""
fi

# 可通过 JAVA_OPTS 环境变量传入额外 JVM 参数
JAVA_OPTS="${JAVA_OPTS:-}"

exec java $JAVA_OPTS -jar "$JAR_FILE" "$@"
