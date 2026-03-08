// sprint-e-demo.agent.groovy
datasource('my_h2_db') {
    type 'h2'
    url 'jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1'
    username 'sa'
    password ''
}

agent('data_assistant') {
    model {
        provider 'gemini'
        modelName 'gemini-2.5-flash'
    }

    systemPrompt """
        你是一个数据助理。使用提供的工具为用户服务，你可以使用内置数据源 'my_h2_db' 进行数据库交互。
    """

    // 引入内置工具
    tools {
        include 'excel_read'
        include 'excel_write'
        include 'pdf_read'
        include 'cmd_execute'
        include 'db_query'
        include 'db_execute'
    }

    datasources {
        attach 'my_h2_db'
    }
}

// 运行示例：
// shell/agentdsl.sh run examples/sprint-e-demo.agent.groovy --chat "用excel生成一个小学课程表的的示例"
// shell/agentdsl.sh run examples/sprint-e-demo.agent.groovy --chat "当前目录有个小学课程表.xlsx的文件看下是什么内容"
// shell/agentdsl.sh run examples/sprint-e-demo.agent.groovy --chat "查一下数据库里面有没有一张test表"
// shell/agentdsl.sh run examples/sprint-e-demo.agent.groovy --chat "写入100条随机数据到test表中，如果没有这个表就建一个"
// shell/agentdsl.sh run examples/sprint-e-demo.agent.groovy --chat "看下index.pdf文件内容主要讲的什么"
// shell/agentdsl.sh run examples/sprint-e-demo.agent.groovy --chat "查看一下当前目录下的文件，分析一下这个项目是干啥用"
