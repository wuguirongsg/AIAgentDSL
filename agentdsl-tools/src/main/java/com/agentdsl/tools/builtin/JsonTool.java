package com.agentdsl.tools.builtin;

import com.agentdsl.core.annotation.AgentTool;
import com.agentdsl.core.annotation.ToolParam;
import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 内置 JSON 工具。
 * 提供 JSON 解析和路径查询功能，使用 Gson 实现。
 */
public class JsonTool {

    private static final Logger log = LoggerFactory.getLogger(JsonTool.class);
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @AgentTool(name = "json_parse", description = "解析 JSON 字符串并返回格式化后的结果")
    public String jsonParse(@ToolParam(description = "JSON 字符串") String json) {
        try {
            JsonElement element = JsonParser.parseString(json);
            return gson.toJson(element);
        } catch (Exception e) {
            log.error("JSON 解析失败", e);
            return "Error: JSON parse failed - " + e.getMessage();
        }
    }

    @AgentTool(name = "json_query", description = "使用 dot-notation 路径查询 JSON 中的值，例如 'user.name' 或 'items.0.title'")
    public String jsonQuery(
            @ToolParam(description = "JSON 字符串") String json,
            @ToolParam(description = "查询路径，使用点号分隔，如 'user.name' 或 'items.0.title'") String path) {
        try {
            JsonElement current = JsonParser.parseString(json);

            String[] segments = path.split("\\.");
            for (String segment : segments) {
                if (current == null || current.isJsonNull()) {
                    return "null";
                }
                if (current.isJsonObject()) {
                    JsonObject obj = current.getAsJsonObject();
                    if (obj.has(segment)) {
                        current = obj.get(segment);
                    } else {
                        return "null";
                    }
                } else if (current.isJsonArray()) {
                    try {
                        int index = Integer.parseInt(segment);
                        JsonArray arr = current.getAsJsonArray();
                        if (index >= 0 && index < arr.size()) {
                            current = arr.get(index);
                        } else {
                            return "Error: Index " + index + " out of bounds (size: " + arr.size() + ")";
                        }
                    } catch (NumberFormatException e) {
                        return "Error: Cannot access property '" + segment + "' on a JSON array";
                    }
                } else {
                    return "Error: Cannot navigate into " + current.getClass().getSimpleName();
                }
            }

            if (current == null || current.isJsonNull()) {
                return "null";
            }
            if (current.isJsonPrimitive()) {
                JsonPrimitive prim = current.getAsJsonPrimitive();
                if (prim.isString())
                    return prim.getAsString();
                return prim.toString();
            }
            return gson.toJson(current);
        } catch (Exception e) {
            log.error("JSON 查询失败: path={}", path, e);
            return "Error: JSON query failed - " + e.getMessage();
        }
    }
}
