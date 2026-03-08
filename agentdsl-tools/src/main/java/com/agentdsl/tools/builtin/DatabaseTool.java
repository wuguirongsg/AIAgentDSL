package com.agentdsl.tools.builtin;

import com.agentdsl.core.annotation.AgentTool;
import com.agentdsl.core.annotation.ToolParam;
import com.agentdsl.core.spec.DataSourceRegistry;
import com.agentdsl.core.spec.DataSourceSpec;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据库交互工具。
 * 允许代理执行授权的 SQL 操作。
 */
public class DatabaseTool {
    private static final Logger log = LoggerFactory.getLogger(DatabaseTool.class);
    private final Gson gson;

    public DatabaseTool() {
        this.gson = new GsonBuilder().create();
    }

    @AgentTool(name = "db_query", description = "执行 SQL 查询 (SELECT) 语句并返回 JSON 格式的结果集")
    public String dbQuery(
            @ToolParam(name = "datasource", description = "数据源名称（在 DSL datasource 中声明）") String datasourceName,
            @ToolParam(name = "sql", description = "SQL SELECT 查询语句") String sql) {

        if (sql == null || !sql.trim().toUpperCase().startsWith("SELECT")) {
            return "Error: Only SELECT statements are allowed in db_query.";
        }

        DataSourceSpec ds = DataSourceRegistry.get(datasourceName);
        if (ds == null) {
            return "Error: DataSource not found: " + datasourceName;
        }

        try (Connection conn = getConnection(ds);
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            List<Map<String, Object>> rows = new ArrayList<>();

            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.put(metaData.getColumnName(i), rs.getObject(i));
                }
                rows.add(row);
            }
            log.info("Query returned {} rows from datasource '{}'", rows.size(), datasourceName);
            return gson.toJson(rows);

        } catch (SQLException e) {
            log.error("Database query failed: {}", sql, e);
            return "Error executing query: " + e.getMessage();
        }
    }

    @AgentTool(name = "db_execute", description = "执行 DML (INSERT/UPDATE/DELETE) 或 DDL 语句。返回影响的行数或执行结果。")
    public String dbExecute(
            @ToolParam(name = "datasource", description = "数据源名称") String datasourceName,
            @ToolParam(name = "sql", description = "SQL 执行语句") String sql) {

        DataSourceSpec ds = DataSourceRegistry.get(datasourceName);
        if (ds == null) {
            return "Error: DataSource not found: " + datasourceName;
        }

        try (Connection conn = getConnection(ds);
                Statement stmt = conn.createStatement()) {

            boolean isResultSet = stmt.execute(sql);
            if (isResultSet) {
                return "Error: Statement returned a result set. Please use db_query for SELECT statements.";
            } else {
                int updateCount = stmt.getUpdateCount();
                log.info("Executed statement on '{}', affected rows: {}", datasourceName, updateCount);
                return "Success: Statement executed successfully. Affected rows: " + updateCount;
            }

        } catch (SQLException e) {
            log.error("Database execute failed: {}", sql, e);
            return "Error executing statement: " + e.getMessage();
        }
    }

    private Connection getConnection(DataSourceSpec ds) throws SQLException {
        if (ds.getUsername() != null && !ds.getUsername().isEmpty()) {
            return DriverManager.getConnection(ds.getUrl(), ds.getUsername(), ds.getPassword());
        } else {
            return DriverManager.getConnection(ds.getUrl());
        }
    }
}
