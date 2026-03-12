package com.agentdsl.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 基于 MCP 官方 Registry API 的发现服务。
 * <p>
 * 使用 https://registry.modelcontextprotocol.io/v0/servers?search={keyword}&limit=N
 * 代替 HTML 爬取，返回结构化 JSON，解析 packages 字段得到可运行命令。
 */
public class SimpleMcpDiscoveryService implements McpDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(SimpleMcpDiscoveryService.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final int SEARCH_LIMIT = 10;

    /** 官方 MCP Registry API 端点 */
    private static final String MCP_REGISTRY_API = "https://registry.modelcontextprotocol.io/v0/servers";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SimpleMcpDiscoveryService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    SimpleMcpDiscoveryService(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public List<DiscoveredMcpServer> discover(String registry, String missingToolName, String userMessage) {
        log.info("[MCP-AUDIT] discover start, registry={}, missingTool={}", registry, missingToolName);

        String keyword = (missingToolName == null || missingToolName.isBlank())
                ? extractSearchKeyword(userMessage)
                : missingToolName;

        try {
            String apiUrl = MCP_REGISTRY_API + "?search="
                    + URLEncoder.encode(keyword, StandardCharsets.UTF_8)
                    + "&limit=" + SEARCH_LIMIT;

            log.info("[MCP-AUDIT] querying registry API, keyword='{}', url={}", keyword, apiUrl);

            String json = httpGet(apiUrl);
            if (json == null) {
                log.warn("[MCP-AUDIT] registry API returned null for keyword='{}'", keyword);
                return List.of();
            }

            JsonNode root = objectMapper.readTree(json);
            JsonNode servers = root.path("servers");
            if (!servers.isArray() || servers.isEmpty()) {
                log.warn("[MCP-AUDIT] registry API returned 0 servers for keyword='{}'", keyword);
                return List.of();
            }

            log.info("[MCP-AUDIT] registry API found {} servers for keyword='{}'", servers.size(), keyword);

            // 收集所有可运行的候选，调用方依次尝试连接
            List<DiscoveredMcpServer> candidates = new ArrayList<>();
            for (JsonNode item : servers) {
                JsonNode server = item.path("server");
                String serverName = server.path("name").asText("unknown");
                extractRunnableCommand(serverName, server).ifPresent(candidates::add);
            }

            if (candidates.isEmpty()) {
                log.warn("[MCP-AUDIT] discover finished, no runnable package in {} servers", servers.size());
            } else {
                log.info("[MCP-AUDIT] discover found {} runnable candidates: {}",
                        candidates.size(),
                        candidates.stream().map(c -> c.command().toString()).toList());
            }
            return candidates;

        } catch (Exception e) {
            log.error("[MCP-AUDIT] discover exception: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 从 server JSON 节点的 packages 数组中提取可运行命令。
     * <ul>
     *   <li>npm + stdio → {@code npx -y <identifier>}</li>
     *   <li>docker       → {@code docker run -i --rm <identifier>}</li>
     * </ul>
     */
    private Optional<DiscoveredMcpServer> extractRunnableCommand(String serverName, JsonNode server) {
        JsonNode packages = server.path("packages");
        if (!packages.isArray()) return Optional.empty();

        for (JsonNode pkg : packages) {
            String registryType = pkg.path("registryType").asText("");
            String identifier = pkg.path("identifier").asText("");
            if (identifier.isBlank()) continue;

            String transportType = pkg.path("transport").path("type").asText("stdio");

            if ("npm".equals(registryType) && "stdio".equals(transportType)) {
                // npm stdio 包：用 npx -y 即可自动安装运行
                List<String> cmd = List.of("npx", "-y", identifier);
                log.debug("[MCP-AUDIT] npm stdio package found: {}", cmd);
                return Optional.of(new DiscoveredMcpServer("dynamic-" + sanitizeName(serverName), cmd));
            }

            if ("docker".equals(registryType)) {
                List<String> cmd = List.of("docker", "run", "-i", "--rm", identifier);
                log.debug("[MCP-AUDIT] docker package found: {}", cmd);
                return Optional.of(new DiscoveredMcpServer("dynamic-" + sanitizeName(serverName), cmd));
            }
        }

        return Optional.empty();
    }

    private String sanitizeName(String name) {
        // 取最后一个 / 后的部分作为简短名称
        int slash = name.lastIndexOf('/');
        String short_ = slash >= 0 ? name.substring(slash + 1) : name;
        return short_.replaceAll("[^a-zA-Z0-9_-]", "-").toLowerCase(Locale.ROOT);
    }

    /**
     * 从用户消息中提取适合 MCP Registry 搜索的英文关键词。
     */
    private String extractSearchKeyword(String text) {
        if (text == null || text.isBlank()) return "tool";
        String t = text.toLowerCase(Locale.ROOT);

        if (t.contains("天气") || t.contains("weather") || t.contains("温度") || t.contains("气温")
                || t.contains("预报") || t.contains("下雨") || t.contains("晴")) return "weather";
        if (t.contains("地图") || t.contains("导航") || t.contains("路线") || t.contains("地址")
                || t.contains("位置") || t.contains("map") || t.contains("高德") || t.contains("amap"))
            return "map";
        if (t.contains("github") || t.contains("仓库") || t.contains("代码") || t.contains("commit")
                || t.contains("pull request") || t.contains("issue")) return "github";
        if (t.contains("文件") || t.contains("目录") || t.contains("读文件") || t.contains("写文件")
                || t.contains("filesystem") || t.contains("file")) return "filesystem";
        if (t.contains("搜索") || t.contains("网页") || t.contains("search") || t.contains("web"))
            return "web search";
        if (t.contains("数据库") || t.contains("sql") || t.contains("mysql") || t.contains("postgres")
                || t.contains("database")) return "database";
        if (t.contains("浏览器") || t.contains("网站") || t.contains("playwright") || t.contains("puppeteer")
                || t.contains("browser") || t.contains("自动化")) return "browser";
        if (t.contains("邮件") || t.contains("email") || t.contains("mail")) return "email";
        if (t.contains("日历") || t.contains("日程") || t.contains("calendar")) return "calendar";
        if (t.contains("slack") || t.contains("通知")) return "slack";
        if (t.contains("notion")) return "notion";
        if (t.contains("jira") || t.contains("confluence")) return "jira";

        // fallback：提取文本中已有的英文词
        java.util.regex.Matcher m = Pattern.compile("[a-zA-Z]{3,}").matcher(text);
        if (m.find()) return m.group().toLowerCase(Locale.ROOT);

        return "tool";
    }

    private String httpGet(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", "AgentDSL-McpDiscovery/1.0")
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("[MCP-AUDIT] HTTP {} for {}", response.statusCode(), url);
                return null;
            }
            return response.body();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[MCP-AUDIT] HTTP interrupted for {}", url);
            return null;
        } catch (Exception e) {
            log.error("[MCP-AUDIT] HTTP failed for {}: {}", url, e.getMessage());
            return null;
        }
    }
}
