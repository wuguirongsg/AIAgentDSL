agent('researcher') {
    description 'A researcher agent that uses web search to answer questions.'

    model {
        provider 'gemini'
        modelName 'gemini-2.5-flash' // Replace with your model
    }

    // Explicitly configure the search provider (tavily, serper, zhipu)
    search {
        provider 'tavily'
        apiKey env('TAVILY_API_KEY') // Assumes you have TAVILY_API_KEY exported in your environment
    }
    // search {
    //     provider 'zhipu'
    //     apiKey env('ZHIPU_API_KEY') // Assumes you have TAVILY_API_KEY exported in your environment
    // }

    systemPrompt "You are a helpful research assistant. When asked about current events or when you need up-to-date information, ALWAYS use the 'web_search' tool. After searching, synthesize the information clearly and cite the sources/links provided by the search tool."

    tools {
        include 'web_search'
    }
}

// run agent example:
// export TAVILY_API_KEY=your api key
// shell/agentdsl.sh run examples/search-demo.agent.groovy --chat "What is the current price of Bitcoin?"
