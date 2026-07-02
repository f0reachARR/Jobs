package me.f0reach.jobs.persistence.mysql;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

/**
 * resources/sql/mysql/schema.sql を読んで CREATE TABLE IF NOT EXISTS を発行する。
 * spec/05-persistence.md の DDL を素直に読む単純実装。
 */
public final class SchemaInitializer {

    private static final String SCHEMA_RESOURCE = "/sql/mysql/schema.sql";

    private final DataSource dataSource;

    public SchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void initialize() throws IOException, SQLException {
        String sql = readSchemaScript();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            for (String statement : splitStatements(sql)) {
                if (statement.isBlank()) continue;
                stmt.execute(statement);
            }
        }
    }

    private String readSchemaScript() throws IOException {
        try (InputStream in = SchemaInitializer.class.getResourceAsStream(SCHEMA_RESOURCE)) {
            if (in == null) {
                throw new IOException("schema.sql not found on classpath: " + SCHEMA_RESOURCE);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    // -- で始まる SQL コメント行は除去
                    if (line.stripLeading().startsWith("--")) continue;
                    sb.append(line).append('\n');
                }
                return sb.toString();
            }
        }
    }

    /** ; 区切りで単純に分割する。多行 SQL 内に ; がないことを前提とする。 */
    private String[] splitStatements(String sql) {
        return sql.split(";\\s*");
    }
}
