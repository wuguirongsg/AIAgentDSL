package com.agentdsl.tools.builtin;

import com.agentdsl.core.spec.DataSourceRegistry;
import com.agentdsl.core.spec.DataSourceSpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import com.google.gson.Gson;

import static org.junit.jupiter.api.Assertions.*;

public class DatabaseToolTest {

    @BeforeAll
    public static void setup() {
        DataSourceSpec spec = new DataSourceSpec("test_db");
        spec.setType("h2");
        spec.setUrl("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
        spec.setUsername("sa");
        spec.setPassword("");
        DataSourceRegistry.register(spec);
    }

    @Test
    public void testDatabaseExecuteAndQuery() {
        DatabaseTool dbTool = new DatabaseTool();

        // 1. Create table
        String ddl = dbTool.dbExecute("test_db",
                "CREATE TABLE IF NOT EXISTS users (id INT PRIMARY KEY, name VARCHAR(255))");
        assertTrue(ddl.contains("executed successfully"));

        // 2. Insert rows
        String insert1 = dbTool.dbExecute("test_db", "INSERT INTO users (id, name) VALUES (1, 'Alice')");
        assertTrue(insert1.contains("Affected rows: 1"));
        String insert2 = dbTool.dbExecute("test_db", "INSERT INTO users (id, name) VALUES (2, 'Bob')");
        assertTrue(insert2.contains("Affected rows: 1"));

        // 3. Query
        String queryResult = dbTool.dbQuery("test_db", "SELECT * FROM users ORDER BY id");
        Gson gson = new Gson();
        List<Map<String, Object>> rows = gson.fromJson(queryResult, List.class);

        assertEquals(2, rows.size());
        assertEquals("Alice", rows.get(0).get("NAME"));
        // The ID might be parsed as Double because of Gson reading JSON number
        assertEquals(1.0, ((Number) rows.get(0).get("ID")).doubleValue());
    }
}
