#!/usr/bin/env bash
# ============================================================
# AgentDSL — 编译打包脚本 (Mac / Linux)
# 用法: ./bin/build.sh
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "🔨 AgentDSL — 开始编译打包 ..."
echo "   项目根目录: $PROJECT_ROOT"
echo ""

cd "$PROJECT_ROOT"

# 1. 赋予 gradlew 执行权限
chmod +x gradlew

# 2. 执行 Gradle clean + shadowJar 构建
echo "📦 执行 Gradle 构建 (clean + shadowJar) ..."
./gradlew clean :agentdsl-cli:shadowJar --warning-mode all

# 3. 检查产物
JAR_FILE="$PROJECT_ROOT/agentdsl-cli/build/libs/agentdsl.jar"
if [ -f "$JAR_FILE" ]; then
    JAR_SIZE=$(du -sh "$JAR_FILE" | cut -f1)
    echo ""
    echo "✅ 构建成功！"
    echo "   产物路径: $JAR_FILE"
    echo "   文件大小: $JAR_SIZE"
    echo ""
    echo "💡 使用以下命令运行 CLI:"
    echo "   java -jar $JAR_FILE --help"
    echo "   或使用: ./bin/agentdsl.sh run examples/simple-chat.agent.groovy --chat \"你好\""
else
    echo "❌ 构建失败：未生成 agentdsl.jar"
    exit 1
fi
