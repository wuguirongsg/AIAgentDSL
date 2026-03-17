// 代码安全检查开关（默认启用）：
//   启用时，urllib.request / subprocess / os.system / rm -rf 等危险操作会被拦截。
//   如需允许网络请求等操作，在启动前设置环境变量：
//     export AGENTDSL_CODE_SECURITY_DISABLED=true
//   或通过 JVM 系统属性：
//     -Dagentdsl.code.security.disabled=true

agent('code-executor-demo') {
    model {
        provider 'gemini'
        modelName 'gemini-3.1-pro-preview'
        // Gemini 思考模型的 thought_signature 传递机制（两个参数缺一不可，均默认 true）：
        //   returnThinking=true：解析响应时把 thought_signature 存入 AiMessage
        //   sendThinking=true  ：下一轮请求时把签名取出并回传给 Gemini
        // 任一为 false → Gemini API 返回 400 INVALID_ARGUMENT。
        // 非思考模型可通过 settings { returnThinking false; sendThinking false } 关闭。
    }

    tools {
        include 'groovy_execute'
        include 'shell_script_run'
        include 'python_run'
        include 'cmd_execute'
        include 'file_write'
        include 'file_read'
    }

    systemPrompt '''你是一个全能的助手，特别擅长使用代码执行工具完成任务；
    精通groovy、shell、python三种编程语言，能够根据任务需求选择合适的编程语言和工具完成任务。
    当收入需要执行的任务需求时，会先思考一下哪种编程语言和工具最适合完成任务，然后选择合适的编程语言生成可以完成任务的代码，然后执行代码完成任务。
你可以执行三种类型的代码：
1. groovy_execute - 执行 Groovy 代码，适合轻量数据处理
2. shell_script_run - 执行 Shell/Bat/PowerShell 脚本
3. python_run - 执行 Python 脚本

请根据任务需求选择合适的执行方式。'''
}
