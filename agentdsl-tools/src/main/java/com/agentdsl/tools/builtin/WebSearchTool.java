package com.agentdsl.tools.builtin;

import com.agentdsl.core.spec.AgentSpec;
import com.agentdsl.core.spec.SearchSpec;
import com.agentdsl.core.annotation.AgentTool;
import com.agentdsl.core.annotation.ToolParam;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchResults;
import dev.langchain4j.web.search.tavily.TavilyWebSearchEngine;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 通用网络搜索工具，支持 Tavily, Serper (Google), 智谱 等底层引擎的动态切换。
 */
public class WebSearchTool {

    private static final Gson gson = new Gson();
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Agent 规范对象，可以从中读取全局配置。
     * 本机制需要在运行时由 Registry/Executor 注入（目前简化为从线程上下文或单例读取，实际需完善上下文传递机制，这里暂缓强依赖）。
     */
    private AgentSpec currentAgentSpec;

    public void setCurrentAgentSpec(AgentSpec currentAgentSpec) {
        this.currentAgentSpec = currentAgentSpec;
    }

    @AgentTool(name = "web_search", description = "在互联网上进行实时检索并将结果提炼返回。适用于获取最新资讯、竞品信息或事实核查。")
    public String webSearch(
            @ToolParam(description = "搜索查询的关键字或自然语言长句") String query,
            @ToolParam(description = "搜索引擎 Provider (如 tavily, serper, zhipu)，若在 DSL 中已统一配置则无需输入", required = false) String providerOverride) {

        SearchSpec spec = currentAgentSpec != null ? currentAgentSpec.getSearchConfig() : null;

        // 默认策略
        String provider = "tavily";
        String apiKey = System.getenv("TAVILY_API_KEY");

        // 覆盖策略：DSL search {} 配置优先
        if (spec != null) {
            if (spec.getProvider() != null)
                provider = spec.getProvider();
            if (spec.getApiKey() != null)
                apiKey = spec.getApiKey();
        }

        // 覆盖策略：显式入参最高优先
        if (providerOverride != null && !providerOverride.isEmpty()) {
            provider = providerOverride;
        }

        // 再次兜底从环境变量获取各类 Key
        if (apiKey == null || apiKey.isEmpty()) {
            if ("tavily".equalsIgnoreCase(provider))
                apiKey = System.getenv("TAVILY_API_KEY");
            if ("serper".equalsIgnoreCase(provider))
                apiKey = System.getenv("SERPER_API_KEY");
            if ("zhipu".equalsIgnoreCase(provider))
                apiKey = System.getenv("ZHIPU_API_KEY");
        }

        if (apiKey == null || apiKey.isEmpty()) {
            return "Error: 缺少 API Key 配置，无法调用 " + provider + " 搜索，请在 DSL的 search {} 中配置 apiKey，或设置对应的环境变量。";
        }

        try {
            switch (provider.toLowerCase()) {
                case "tavily":
                    return doTavilySearch(apiKey, query);
                case "serper":
                    return doSerperSearch(apiKey, query);
                case "zhipu":
                    return doZhipuSearch(apiKey, query);
                default:
                    return "Error: 不支持的搜索提供商 '" + provider + "'。目前仅支持: tavily, serper, zhipu。";
            }
        } catch (Exception e) {
            return "Error: 搜索请求失败 (" + provider + ") - " + e.getMessage();
        }
    }

    private String doTavilySearch(String apiKey, String query) throws Exception {
        WebSearchEngine engine = TavilyWebSearchEngine.builder()
                .apiKey(apiKey)
                .timeout(Duration.ofSeconds(15))
                .build();

        WebSearchResults results = engine.search(query);
        StringBuilder sb = new StringBuilder();
        sb.append("搜索结果摘要如下：\n");
        for (WebSearchOrganicResult result : results.results()) {
            sb.append("\n- 标题: ").append(result.title());
            sb.append("\n  链接: ").append(result.url().toString());
            sb.append("\n  内容: ").append(result.snippet()).append("\n");
        }
        return sb.toString();
    }

    private String doSerperSearch(String apiKey, String query) throws Exception {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("q", query);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://google.serper.dev/search"))
                .header("X-API-KEY", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Serper API Error: " + response.body());
        }

        JsonObject json = gson.fromJson(response.body(), JsonObject.class);
        JsonArray organic = json.getAsJsonArray("organic");

        if (organic == null || organic.isEmpty()) {
            return "未找到相关的搜索结果。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("搜索结果摘要如下：\n");
        for (int i = 0; i < Math.min(5, organic.size()); i++) {
            JsonObject item = organic.get(i).getAsJsonObject();
            sb.append("\n- 标题: ").append(item.has("title") ? item.get("title").getAsString() : "无标题");
            sb.append("\n  链接: ").append(item.has("link") ? item.get("link").getAsString() : "无链接");
            sb.append("\n  内容: ").append(item.has("snippet") ? item.get("snippet").getAsString() : "").append("\n");
        }
        return sb.toString();
    }

    private String doZhipuSearch(String apiKey, String query) throws Exception {
        // 智谱 Web Search API: https://open.bigmodel.cn/api/paas/v4/tools
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", query);

        JsonArray messages = new JsonArray();
        messages.add(message);

        JsonObject toolDetails = new JsonObject();
        toolDetails.addProperty("type", "web_search");
        JsonObject webSearchConfig = new JsonObject();
        webSearchConfig.addProperty("enable", true);
        webSearchConfig.addProperty("search_query", query); // 强制使用指定 query
        toolDetails.add("web_search", webSearchConfig);

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("request_id", "search_" + System.currentTimeMillis());
        requestBody.addProperty("tool", "web-search-pro"); // tool 代码
        requestBody.add("messages", messages);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://open.bigmodel.cn/api/paas/v4/tools"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Zhipu API Error: " + response.body());
        }

        // 解析包含 "choices" 的返回结构
        JsonObject json = gson.fromJson(response.body(), JsonObject.class);
        JsonArray choices = json.getAsJsonArray("choices");

        if (choices == null || choices.isEmpty()) {
            return "智谱搜索未返回任何内容。";
        }

        JsonObject firstChoice = choices.get(0).getAsJsonObject();
        JsonObject messageObj = firstChoice.getAsJsonObject("message");

        // 简易提取：通常直接返回 content，但也可能有 tool_calls 进一步封装
        if (messageObj != null && messageObj.has("content")) {
            return "根据智谱网络搜索：\n" + messageObj.get("content").getAsString();
        }

        return "未能成功解析智谱网络搜索结果结构: " + response.body();
    }
}
