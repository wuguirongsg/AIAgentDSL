package com.agentdsl.memory.hypergraph.config;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/**
 * Hypergraph memory 插件配置。
 * 内部采用结构化配置，与设计文档中的 stm/ltm/vector/decay/consolidation 子块对齐。
 */
public record HypergraphMemoryConfig(
        String memoryId,
        StmConfig stm,
        LtmConfig ltm,
        VectorConfig vector,
        DecayConfig decay,
        ConsolidationConfig consolidation,
        int summaryMaxLength,
        double deepRecallThreshold) {

    @SuppressWarnings("unchecked")
    public static HypergraphMemoryConfig from(Map<String, Object> config) {
        String memoryId = stringValue(config, "memoryId", UUID.randomUUID().toString());

        Map<String, Object> stmMap = nestedMap(config, "stm");
        Map<String, Object> ltmMap = nestedMap(config, "ltm");
        Map<String, Object> vectorMap = nestedMap(config, "vector");
        Map<String, Object> decayMap = nestedMap(config, "decay");
        Map<String, Object> consolidationMap = nestedMap(config, "consolidation");

        StmConfig stm = new StmConfig(
                intValue(stmMap, "maxEdges",
                        intValue(config, "stmMaxMessages",
                                intValue(config, "maxMessages", 20))),
                intValue(stmMap, "ttlHours", 48));

        String path = stringValue(ltmMap, "path",
                pathFromJdbcUrl(stringValue(config, "sqliteJdbcUrl",
                        "jdbc:sqlite:" + Path.of("build", "hypergraph-memory", memoryId + ".db"))));
        String jdbcUrl = stringValue(config, "sqliteJdbcUrl", toJdbcUrl(path));
        LtmConfig ltm = new LtmConfig(
                stringValue(ltmMap, "backend", "sqlite"),
                path,
                stringValue(ltmMap, "compressionModel", null),
                jdbcUrl);

        String vectorPath = stringValue(vectorMap, "path", defaultArchivePath(path));
        VectorConfig vector = new VectorConfig(
                stringValue(vectorMap, "store", "file-local"),
                stringValue(vectorMap, "embeddingModel", null),
                vectorPath,
                intValue(config, "archiveEmbeddingDimension", 64),
                intValue(config, "archiveSearchK", 3));

        DecayConfig defaultDecay = DecayConfig.defaults();
        DecayConfig decay = new DecayConfig(
                doubleValue(decayMap, "baseRate", defaultDecay.baseDecayRate()),
                doubleValue(decayMap, "importanceBoost", defaultDecay.importanceBoost()),
                defaultDecay.accessBonus(),
                doubleValue(decayMap, "compressionThreshold", defaultDecay.compressionThreshold()),
                doubleValue(decayMap, "archiveThreshold", defaultDecay.archiveThreshold()));

        ConsolidationConfig consolidation = new ConsolidationConfig(
                intValue(consolidationMap, "intervalHours", 6),
                booleanValue(consolidationMap, "autoStart", true));

        return new HypergraphMemoryConfig(
                memoryId,
                stm,
                ltm,
                vector,
                decay,
                consolidation,
                intValue(config, "summaryMaxLength", 240),
                doubleValue(config, "deepRecallThreshold", 0.85));
    }

    private static Map<String, Object> nestedMap(Map<String, Object> root, String key) {
        Object value = root.get(key);
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private static String stringValue(Map<String, Object> config, String key, String defaultValue) {
        Object value = config.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private static int intValue(Map<String, Object> config, String key, int defaultValue) {
        Object value = config.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text.trim());
        }
        return defaultValue;
    }

    private static double doubleValue(Map<String, Object> config, String key, double defaultValue) {
        Object value = config.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Double.parseDouble(text.trim());
        }
        return defaultValue;
    }

    private static boolean booleanValue(Map<String, Object> config, String key, boolean defaultValue) {
        Object value = config.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && !text.isBlank()) {
            return Boolean.parseBoolean(text.trim());
        }
        return defaultValue;
    }

    private static String toJdbcUrl(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        return path.startsWith("jdbc:sqlite:") ? path : "jdbc:sqlite:" + path;
    }

    private static String pathFromJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null) {
            return null;
        }
        String prefix = "jdbc:sqlite:";
        return jdbcUrl.startsWith(prefix) ? jdbcUrl.substring(prefix.length()) : jdbcUrl;
    }

    private static String defaultArchivePath(String ltmPath) {
        if (ltmPath == null || ltmPath.isBlank()) {
            return Path.of("build", "hypergraph-memory", "archive-store.json").toString();
        }
        Path ltmFile = Path.of(ltmPath);
        String fileName = ltmFile.getFileName() != null ? ltmFile.getFileName().toString() : "memory.db";
        int extensionIndex = fileName.lastIndexOf('.');
        String baseName = extensionIndex > 0 ? fileName.substring(0, extensionIndex) : fileName;
        Path parent = ltmFile.getParent();
        Path archiveFile = Path.of(baseName + ".archive-store.json");
        return parent != null ? parent.resolve(archiveFile).toString() : archiveFile.toString();
    }
}
