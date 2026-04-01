package com.agentdsl.memory.hypergraph.store;

import com.agentdsl.memory.hypergraph.model.HyperEdge;
import com.agentdsl.memory.hypergraph.model.MemoryNode;
import com.agentdsl.memory.hypergraph.model.MemoryTier;
import com.agentdsl.memory.hypergraph.model.MetaHyperEdge;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.Set;

public class SQLiteLtmStore implements LtmStore {

    private final String jdbcUrl;

    public SQLiteLtmStore(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
        initialize();
    }

    @Override
    public void save(HyperEdge edge) {
        String sql = """
                insert or replace into hypergraph_ltm (
                    id, memory_id, message_type, message_text, tool_request_id, tool_name,
                    weight, importance, tier, ltm_level, summary, archive_pointers, node_ids,
                    access_count, anchor, created_at, last_accessed_at, emotion_tag, context_tags,
                    linked_edge_ids, meta_edge_id
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, edge.id());
            statement.setString(2, edge.memoryId());
            statement.setString(3, edge.messageType());
            statement.setString(4, edge.messageText());
            statement.setString(5, edge.toolRequestId());
            statement.setString(6, edge.toolName());
            statement.setDouble(7, edge.weight());
            statement.setDouble(8, edge.importance());
            statement.setString(9, edge.tier().name());
            statement.setInt(10, edge.ltmLevel());
            statement.setString(11, edge.compressedSummary());
            statement.setString(12, join(edge.archivePointers()));
            statement.setString(13, join(edge.nodeIds()));
            statement.setInt(14, edge.accessCount());
            statement.setBoolean(15, edge.anchor());
            statement.setString(16, edge.createdAt().toString());
            statement.setString(17, edge.lastAccessedAt().toString());
            statement.setString(18, edge.emotionTag() != null ? edge.emotionTag().name() : null);
            statement.setString(19, join(edge.contextTags()));
            statement.setString(20, join(edge.linkedEdgeIds()));
            statement.setString(21, edge.metaEdgeId());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("写入 SQLite LTM 失败", e);
        }
    }

    @Override
    public List<HyperEdge> findAll(String memoryId) {
        String sql = """
                select id, memory_id, message_type, message_text, tool_request_id, tool_name,
                       weight, importance, tier, ltm_level, summary, archive_pointers, node_ids,
                       access_count, anchor, created_at, last_accessed_at, emotion_tag, context_tags,
                       linked_edge_ids, meta_edge_id
                from hypergraph_ltm
                where memory_id = ?
                order by created_at asc
                """;
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, memoryId);
            return readEdges(statement);
        } catch (SQLException e) {
            throw new IllegalStateException("读取 SQLite LTM 失败", e);
        }
    }

    @Override
    public List<HyperEdge> semanticSearch(String memoryId, String query, int limit) {
        List<String> queryTokens = tokenize(query);
        int safeLimit = Math.max(1, limit);
        List<HyperEdge> all = findAll(memoryId);

        // 主路径：字面得分 > 0 的候选，按分数排序取 topK
        List<ScoredEdge> textMatched = all.stream()
                .map(edge -> new ScoredEdge(edge, score(edge, queryTokens)))
                .filter(scored -> scored.score() > 0)
                .sorted((l, r) -> Double.compare(r.score(), l.score()))
                .limit(safeLimit)
                .toList();
        if (!textMatched.isEmpty()) {
            return textMatched.stream().map(ScoredEdge::edge).toList();
        }

        // 兜底路径：字面完全无命中时（如"你知道我是谁吗"），返回重要度最高的 topK 条目
        // 保证高重要度事实（个人身份、偏好）始终能作为 DeepRecallEngine 的候选进行向量重排
        return all.stream()
                .sorted((l, r) -> Double.compare(
                        Math.max(r.importance(), r.weight()),
                        Math.max(l.importance(), l.weight())))
                .limit(safeLimit)
                .toList();
    }

    @Override
    public void markArchived(String edgeId) {
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
                PreparedStatement statement = connection
                        .prepareStatement("update hypergraph_ltm set tier = ? where id = ?")) {
            statement.setString(1, MemoryTier.ARCHIVE.name());
            statement.setString(2, edgeId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("更新 SQLite LTM 归档状态失败", e);
        }
    }

    @Override
    public void saveNodes(String memoryId, List<MemoryNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        String sql = """
                insert or replace into hypergraph_nodes (
                    id, memory_id, content, node_type, embedding_json, created_at, last_accessed_at, access_count
                ) values (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (MemoryNode node : nodes) {
                statement.setString(1, node.id());
                statement.setString(2, memoryId);
                statement.setString(3, node.content());
                statement.setString(4, node.nodeType().name());
                statement.setString(5, joinFloats(node.embedding()));
                statement.setString(6, node.createdAt().toString());
                statement.setString(7, node.lastAccessedAt().toString());
                statement.setInt(8, node.accessCount());
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException e) {
            throw new IllegalStateException("写入 hypergraph nodes 失败", e);
        }
    }

    @Override
    public List<HyperEdge> findRelatedEdges(String memoryId, HyperEdge edge, int limit) {
        if (edge == null) {
            return List.of();
        }
        Set<String> nodeIds = new LinkedHashSet<>(edge.nodeIds() != null ? edge.nodeIds() : List.of());
        Set<String> tags = new LinkedHashSet<>(edge.contextTags() != null ? edge.contextTags() : List.of());
        return findAll(memoryId).stream()
                .filter(existing -> !existing.id().equals(edge.id()))
                .map(existing -> new ScoredEdge(existing, relationScore(existing, nodeIds, tags)))
                .filter(scored -> scored.score() > 0)
                .sorted((left, right) -> Double.compare(right.score(), left.score()))
                .limit(Math.max(1, limit))
                .map(ScoredEdge::edge)
                .toList();
    }

    @Override
    public void replaceLinkedEdgeIds(String edgeId, List<String> linkedEdgeIds) {
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
                PreparedStatement statement = connection
                        .prepareStatement("update hypergraph_ltm set linked_edge_ids = ? where id = ?")) {
            statement.setString(1, join(linkedEdgeIds));
            statement.setString(2, edgeId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("更新 linked_edge_ids 失败", e);
        }
    }

    @Override
    public void clear(String memoryId) {
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
                PreparedStatement statement = connection
                        .prepareStatement("delete from hypergraph_ltm where memory_id = ?")) {
            statement.setString(1, memoryId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("清理 SQLite LTM 失败", e);
        }
    }

    private void initialize() {
        ensureParentDirectory();
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
                Statement statement = connection.createStatement()) {
            // schema.sql 包含多条 DDL，SQLite JDBC 对整段脚本的 execute 只会执行首条语句，
            // 因此这里显式逐句执行，确保 hypergraph_ltm 和 hypergraph_nodes 都能创建出来。
            executeSchema(statement, loadSchema());
            ensureColumn(statement, "hypergraph_ltm", "weight", "real not null default 1.0");
            ensureColumn(statement, "hypergraph_ltm", "node_ids", "text");
            ensureColumn(statement, "hypergraph_ltm", "emotion_tag", "text");
            ensureColumn(statement, "hypergraph_ltm", "context_tags", "text");
            ensureColumn(statement, "hypergraph_ltm", "linked_edge_ids", "text");
            ensureColumn(statement, "hypergraph_ltm", "ltm_level", "integer not null default 0");
            ensureColumn(statement, "hypergraph_ltm", "meta_edge_id", "text");
        } catch (SQLException e) {
            throw new IllegalStateException("初始化 SQLite LTM 失败", e);
        }
    }

    private void ensureParentDirectory() {
        String prefix = "jdbc:sqlite:";
        if (!jdbcUrl.startsWith(prefix)) {
            return;
        }
        Path dbPath = Path.of(jdbcUrl.substring(prefix.length()));
        Path parent = dbPath.getParent();
        if (parent == null) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            throw new IllegalStateException("创建 SQLite 目录失败: " + parent, e);
        }
    }

    private String loadSchema() {
        try (InputStream inputStream = getClass().getClassLoader()
                .getResourceAsStream("db/schema.sql")) {
            if (inputStream == null) {
                throw new IllegalStateException("未找到 SQLite schema.sql");
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取 SQLite schema.sql 失败", e);
        }
    }

    private List<HyperEdge> readEdges(PreparedStatement statement) throws SQLException {
        try (var resultSet = statement.executeQuery()) {
            List<HyperEdge> result = new ArrayList<>();
            while (resultSet.next()) {
                result.add(new HyperEdge(
                        resultSet.getString("id"),
                        resultSet.getString("memory_id"),
                        resultSet.getString("message_type"),
                        resultSet.getString("message_text"),
                        resultSet.getString("tool_request_id"),
                        resultSet.getString("tool_name"),
                        resultSet.getDouble("weight"),
                        resultSet.getDouble("importance"),
                        MemoryTier.valueOf(resultSet.getString("tier")),
                        resultSet.getInt("ltm_level"),
                        resultSet.getString("summary"),
                        splitPointers(resultSet.getString("archive_pointers")),
                        splitPointers(resultSet.getString("node_ids")),
                        resultSet.getInt("access_count"),
                        resultSet.getBoolean("anchor"),
                        Instant.parse(resultSet.getString("created_at")),
                        Instant.parse(resultSet.getString("last_accessed_at")),
                        readEmotionTag(resultSet.getString("emotion_tag")),
                        splitPointers(resultSet.getString("context_tags")),
                        splitPointers(resultSet.getString("linked_edge_ids")),
                        resultSet.getString("meta_edge_id")));
            }
            return result;
        }
    }

    private String join(List<String> values) {
        return values == null || values.isEmpty() ? "" : String.join(",", values);
    }

    private List<String> splitPointers(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.split(","));
    }

    private double score(HyperEdge edge, List<String> queryTokens) {
        if (queryTokens.isEmpty()) {
            return 0;
        }
        String docText = ((edge.compressedSummary() != null ? edge.compressedSummary() : "") + " "
                + (edge.messageText() != null ? edge.messageText() : "") + " "
                + String.join(" ", edge.contextTags() != null ? edge.contextTags() : List.of()))
                .toLowerCase(Locale.ROOT);
        List<String> docTokens = tokenize(docText);
        long matched = queryTokens.stream()
                .filter(token -> docTokens.contains(token)
                        || docText.contains(token)
                        || token.contains(docText))
                .count();
        double tokenScore = matched / (double) queryTokens.size();
        double charScore = characterOverlapScore(String.join("", queryTokens), docText);
        double combined = Math.max(tokenScore, charScore);
        if (combined == 0) {
            return 0;
        }
        return combined * (0.5 + Math.max(edge.importance(), edge.weight()) / 2.0);
    }

    private double relationScore(HyperEdge edge, Set<String> nodeIds, Set<String> tags) {
        long sharedNodes = edge.nodeIds() != null
                ? edge.nodeIds().stream().filter(nodeIds::contains).count()
                : 0;
        long sharedTags = edge.contextTags() != null
                ? edge.contextTags().stream().filter(tags::contains).count()
                : 0;
        if (sharedNodes == 0 && sharedTags == 0) {
            return 0;
        }
        // 节点重合比标签重合信号更强，因此给予更高权重，便于形成稳定的边间链接。
        return sharedNodes * 2.0 + sharedTags;
    }

    private void ensureColumn(Statement statement, String tableName, String columnName, String ddl) throws SQLException {
        try (var resultSet = statement.executeQuery("pragma table_info(" + tableName + ")")) {
            while (resultSet.next()) {
                if (columnName.equalsIgnoreCase(resultSet.getString("name"))) {
                    return;
                }
            }
        }
        statement.execute("alter table " + tableName + " add column " + columnName + " " + ddl);
    }

    private void executeSchema(Statement statement, String schemaSql) throws SQLException {
        for (String sql : schemaSql.split(";")) {
            String trimmed = sql.trim();
            if (!trimmed.isBlank()) {
                statement.execute(trimmed);
            }
        }
    }

    private String joinFloats(List<Float> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.stream().map(String::valueOf).reduce((left, right) -> left + "," + right).orElse(null);
    }

    private com.agentdsl.memory.hypergraph.model.EmotionTag readEmotionTag(String value) {
        if (value == null || value.isBlank()) {
            return com.agentdsl.memory.hypergraph.model.EmotionTag.NEUTRAL;
        }
        return com.agentdsl.memory.hypergraph.model.EmotionTag.valueOf(value);
    }

    private List<String> tokenize(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.toLowerCase(Locale.ROOT).split("[^\\p{IsAlphabetic}\\p{IsDigit}\\p{IsIdeographic}]+"))
                .stream()
                .filter(token -> !token.isBlank())
                .toList();
    }

    private double characterOverlapScore(String queryText, String docText) {
        String compactQuery = queryText.replaceAll("\\s+", "");
        if (compactQuery.isBlank() || docText.isBlank()) {
            return 0;
        }
        long matched = compactQuery.chars()
                .distinct()
                .filter(ch -> docText.indexOf(ch) >= 0)
                .count();
        long total = compactQuery.chars().distinct().count();
        return total == 0 ? 0 : matched / (double) total;
    }

    // ===== v1.1 新增方法 =====

    @Override
    public java.util.Optional<HyperEdge> findById(String edgeId) {
        String sql = """
                select id, memory_id, message_type, message_text, tool_request_id, tool_name,
                       weight, importance, tier, ltm_level, summary, archive_pointers, node_ids,
                       access_count, anchor, created_at, last_accessed_at, emotion_tag, context_tags,
                       linked_edge_ids, meta_edge_id
                from hypergraph_ltm where id = ?
                """;
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, edgeId);
            List<HyperEdge> edges = readEdges(statement);
            return edges.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(edges.get(0));
        } catch (SQLException e) {
            throw new IllegalStateException("按 ID 查询 SQLite LTM 失败", e);
        }
    }

    @Override
    public List<HyperEdge> findByLtmLevel(String memoryId, int ltmLevel) {
        String sql = """
                select id, memory_id, message_type, message_text, tool_request_id, tool_name,
                       weight, importance, tier, ltm_level, summary, archive_pointers, node_ids,
                       access_count, anchor, created_at, last_accessed_at, emotion_tag, context_tags,
                       linked_edge_ids, meta_edge_id
                from hypergraph_ltm where memory_id = ? and ltm_level = ? order by created_at asc
                """;
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, memoryId);
            statement.setInt(2, ltmLevel);
            return readEdges(statement);
        } catch (SQLException e) {
            throw new IllegalStateException("按 ltm_level 查询 SQLite LTM 失败", e);
        }
    }

    @Override
    public void updateMetaEdgeId(String edgeId, String metaEdgeId) {
        String sql = "update hypergraph_ltm set meta_edge_id = ? where id = ?";
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, metaEdgeId);
            statement.setString(2, edgeId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("更新 meta_edge_id 失败", e);
        }
    }

    @Override
    public void saveMetaEdge(MetaHyperEdge metaEdge) {
        String sql = """
                insert or replace into hypergraph_meta_edges
                    (id, member_edge_ids, theme_summary, linked_meta_edge_ids, context_tags,
                     cohesion, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, metaEdge.id());
            statement.setString(2, join(metaEdge.memberEdgeIds()));
            statement.setString(3, metaEdge.themeSummary());
            statement.setString(4, join(metaEdge.linkedMetaEdgeIds()));
            statement.setString(5, join(metaEdge.contextTags()));
            statement.setDouble(6, metaEdge.cohesion());
            statement.setString(7, metaEdge.createdAt().toString());
            statement.setString(8, metaEdge.updatedAt().toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("写入 meta_edges 失败", e);
        }
    }

    @Override
    public List<MetaHyperEdge> findAllMetaEdges() {
        String sql = "select * from hypergraph_meta_edges order by created_at asc";
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
                PreparedStatement statement = connection.prepareStatement(sql);
                var resultSet = statement.executeQuery()) {
            List<MetaHyperEdge> result = new ArrayList<>();
            while (resultSet.next()) {
                result.add(new MetaHyperEdge(
                        resultSet.getString("id"),
                        splitPointers(resultSet.getString("member_edge_ids")),
                        resultSet.getString("theme_summary"),
                        splitPointers(resultSet.getString("linked_meta_edge_ids")),
                        splitPointers(resultSet.getString("context_tags")),
                        resultSet.getDouble("cohesion"),
                        java.time.Instant.parse(resultSet.getString("created_at")),
                        java.time.Instant.parse(resultSet.getString("updated_at"))));
            }
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException("查询 meta_edges 失败", e);
        }
    }

    @Override
    public void linkMetaEdges(String metaIdA, String metaIdB) {
        String sql = "insert or ignore into hypergraph_meta_edge_links (meta_id_a, meta_id_b) values (?, ?)";
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, metaIdA);
            statement.setString(2, metaIdB);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("写入 meta_edge_links 失败", e);
        }
    }

    private record ScoredEdge(HyperEdge edge, double score) {
    }
}
