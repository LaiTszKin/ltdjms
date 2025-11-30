package ltdjms.discord.shared;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Performs automatic, non-destructive database schema migrations based on a
 * canonical SQL schema file.
 *
 * <p>The migrator:</p>
 * <ul>
 *   <li>Creates missing tables defined in the canonical schema</li>
 *   <li>Adds new columns that are safe (NULL-able or have a DEFAULT)</li>
 *   <li>Re-applies the canonical schema to ensure indexes, triggers and
 *       functions are present</li>
 *   <li>Detects destructive changes (type changes, dropped/renamed columns,
 *       new NOT NULL columns without DEFAULT) and aborts with an exception</li>
 * </ul>
 */
public final class DatabaseSchemaMigrator {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseSchemaMigrator.class);

    private final String schemaResourcePath;

    /**
     * Creates a migrator that uses the given schema resource on the classpath.
     *
     * @param schemaResourcePath classpath location of the canonical schema,
     *                           for example {@code "db/schema.sql"}
     */
    public DatabaseSchemaMigrator(String schemaResourcePath) {
        this.schemaResourcePath = schemaResourcePath;
    }

    /**
     * Convenience factory using the default application schema.
     */
    public static DatabaseSchemaMigrator forDefaultSchema() {
        return new DatabaseSchemaMigrator("db/schema.sql");
    }

    /**
     * Applies any required non-destructive migrations so the target database
     * matches the canonical schema. Destructive changes MUST trigger a
     * {@link SchemaMigrationException}.
     *
     * @param dataSource target data source
     */
    public void migrate(DataSource dataSource) {
        String schemaSql = loadSchemaSql();
        Map<String, TableDefinition> canonicalTables = parseCanonicalTables(schemaSql);

        if (canonicalTables.isEmpty()) {
            LOG.warn("No CREATE TABLE statements found in schema resource {}", schemaResourcePath);
        }

        try (Connection conn = dataSource.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try {
                List<String> migrationStatements = new ArrayList<>();
                List<String> destructiveReasons = new ArrayList<>();

                for (TableDefinition table : canonicalTables.values()) {
                    if (!tableExists(conn, table.name)) {
                        LOG.info("Planning creation of missing table: {}", table.name);
                        migrationStatements.add(table.createTableSql);
                        continue;
                    }

                    Map<String, ActualColumnInfo> actualColumns = loadActualColumns(conn, table.name);

                    // Handle canonical columns
                    for (ColumnDefinition canonicalColumn : table.columns.values()) {
                        String columnKey = canonicalColumn.name.toLowerCase(Locale.ROOT);
                        ActualColumnInfo actual = actualColumns.remove(columnKey);

                        if (actual == null) {
                            // New column in canonical schema
                            if (canonicalColumn.notNull && !canonicalColumn.hasDefault) {
                                destructiveReasons.add(String.format(
                                        "Table %s: new column %s is NOT NULL without DEFAULT (resource=%s)",
                                        table.name, canonicalColumn.name, schemaResourcePath));
                            } else {
                                String ddl = "ALTER TABLE " + table.name + " ADD COLUMN " + canonicalColumn.fullDefinition + ";";
                                LOG.info("Planning non-destructive column addition: {}", ddl);
                                migrationStatements.add(ddl);
                            }
                        } else {
                            // Existing column: check for destructive changes (type / nullability)
                            if (!canonicalColumn.isCompatibleWith(actual)) {
                                destructiveReasons.add(String.format(
                                        "Table %s: column %s type or nullability differs between database (%s, nullable=%s) and schema (%s, notNull=%s) [resource=%s]",
                                        table.name,
                                        canonicalColumn.name,
                                        actual.normalizedType,
                                        actual.nullable,
                                        canonicalColumn.normalizedType,
                                        canonicalColumn.notNull,
                                        schemaResourcePath));
                            }
                        }
                    }

                    // Any remaining actual columns were not declared in the canonical schema
                    for (ActualColumnInfo extra : actualColumns.values()) {
                        destructiveReasons.add(String.format(
                                "Table %s: database has extra column %s (type=%s) not present in schema resource %s",
                                table.name,
                                extra.name,
                                extra.normalizedType,
                                schemaResourcePath));
                    }
                }

                if (!destructiveReasons.isEmpty()) {
                    String message = "Detected destructive schema differences for " + schemaResourcePath + ": " +
                            String.join("; ", destructiveReasons);
                    LOG.error(message);
                    conn.rollback();
                    throw new SchemaMigrationException(message);
                }

                // Apply planned non-destructive migrations, then re-apply canonical schema to ensure
                // functions, triggers and indexes are present.
                try (Statement stmt = conn.createStatement()) {
                    for (String ddl : migrationStatements) {
                        LOG.info("Applying schema migration DDL: {}", ddl);
                        stmt.execute(ddl);
                    }

                    LOG.info("Re-applying canonical schema from resource {}", schemaResourcePath);
                    stmt.execute(schemaSql);
                }

                conn.commit();
                LOG.info("Schema migration completed successfully for resource {}", schemaResourcePath);
            } catch (SQLException e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    LOG.warn("Failed to rollback transaction after migration error", rollbackEx);
                }
                String message = "Failed to apply schema migrations for " + schemaResourcePath;
                LOG.error(message, e);
                throw new SchemaMigrationException(message, e);
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException e) {
            String message = "Failed to obtain database connection for schema migration using " + schemaResourcePath;
            LOG.error(message, e);
            throw new SchemaMigrationException(message, e);
        }
    }

    private String loadSchemaSql() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = DatabaseSchemaMigrator.class.getClassLoader();
        }

        try (InputStream is = cl.getResourceAsStream(schemaResourcePath)) {
            if (is == null) {
                String message = "Schema resource not found on classpath: " + schemaResourcePath;
                LOG.error(message);
                throw new SchemaMigrationException(message);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            String message = "Failed to load schema resource: " + schemaResourcePath;
            LOG.error(message, e);
            throw new SchemaMigrationException(message, e);
        }
    }

    private boolean tableExists(Connection conn, String tableName) throws SQLException {
        String sql = "SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private Map<String, ActualColumnInfo> loadActualColumns(Connection conn, String tableName) throws SQLException {
        String sql = "SELECT column_name, data_type, is_nullable, character_maximum_length " +
                "FROM information_schema.columns " +
                "WHERE table_schema = 'public' AND table_name = ?";

        Map<String, ActualColumnInfo> result = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("column_name");
                    String dataType = rs.getString("data_type");
                    String isNullable = rs.getString("is_nullable");
                    Integer charLength = rs.getObject("character_maximum_length") != null
                            ? rs.getInt("character_maximum_length")
                            : null;

                    ActualColumnInfo info = new ActualColumnInfo(
                            name,
                            normalizeDatabaseType(dataType, charLength),
                            "YES".equalsIgnoreCase(isNullable)
                    );
                    result.put(name.toLowerCase(Locale.ROOT), info);
                }
            }
        }
        return result;
    }

    private Map<String, TableDefinition> parseCanonicalTables(String schemaSql) {
        Map<String, TableDefinition> tables = new LinkedHashMap<>();

        String upperSql = schemaSql.toUpperCase(Locale.ROOT);
        String keyword = "CREATE TABLE IF NOT EXISTS";
        int index = 0;

        while (true) {
            int start = upperSql.indexOf(keyword, index);
            if (start < 0) {
                break;
            }

            int nameStart = start + keyword.length();
            // Skip whitespace
            while (nameStart < upperSql.length() && Character.isWhitespace(upperSql.charAt(nameStart))) {
                nameStart++;
            }

            // Read table name
            int nameEnd = nameStart;
            while (nameEnd < upperSql.length() && !Character.isWhitespace(upperSql.charAt(nameEnd)) &&
                    upperSql.charAt(nameEnd) != '(') {
                nameEnd++;
            }

            String tableName = schemaSql.substring(nameStart, nameEnd).trim();

            // Find opening parenthesis and matching closing parenthesis
            int openParen = schemaSql.indexOf('(', nameEnd);
            if (openParen < 0) {
                break;
            }

            int closeParen = findMatchingClosingParenthesis(schemaSql, openParen);
            if (closeParen < 0) {
                break;
            }

            // Include trailing semicolon if present
            int statementEnd = closeParen;
            while (statementEnd < schemaSql.length() && schemaSql.charAt(statementEnd) != ';') {
                statementEnd++;
            }
            if (statementEnd < schemaSql.length() && schemaSql.charAt(statementEnd) == ';') {
                statementEnd++;
            }

            String createTableSql = schemaSql.substring(start, statementEnd).trim();
            String columnsSection = schemaSql.substring(openParen + 1, closeParen);

            TableDefinition table = new TableDefinition(tableName, createTableSql);
            parseColumns(columnsSection, table);
            tables.put(tableName.toLowerCase(Locale.ROOT), table);

            index = statementEnd;
        }

        return tables;
    }

    private int findMatchingClosingParenthesis(String sql, int openIndex) {
        int depth = 0;
        for (int i = openIndex; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private void parseColumns(String columnsSection, TableDefinition table) {
        String[] lines = columnsSection.split("\\r?\\n");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("--")) {
                continue;
            }

            // Remove trailing comma if present
            if (line.endsWith(",")) {
                line = line.substring(0, line.length() - 1).trim();
            }

            String upper = line.toUpperCase(Locale.ROOT);
            // Skip table-level constraints
            if (upper.startsWith("CONSTRAINT")
                    || upper.startsWith("PRIMARY KEY")
                    || upper.startsWith("FOREIGN KEY")
                    || upper.startsWith("UNIQUE")
                    || upper.startsWith("CHECK")) {
                continue;
            }

            int firstSpace = line.indexOf(' ');
            if (firstSpace <= 0) {
                continue;
            }

            String columnName = line.substring(0, firstSpace).trim();
            String rest = line.substring(firstSpace + 1).trim();

            boolean notNull = upper.contains("NOT NULL");
            boolean hasDefault = upper.contains("DEFAULT");

            String dataTypeFragment = extractDataTypeFragment(rest);
            String normalizedType = normalizeSchemaType(dataTypeFragment);

            ColumnDefinition columnDefinition = new ColumnDefinition(
                    columnName,
                    line,
                    normalizedType,
                    notNull,
                    hasDefault
            );
            table.columns.put(columnName.toLowerCase(Locale.ROOT), columnDefinition);
        }
    }

    private String extractDataTypeFragment(String rest) {
        String[] tokens = rest.split("\\s+");
        StringBuilder type = new StringBuilder();

        for (String token : tokens) {
            String upper = token.toUpperCase(Locale.ROOT);
            if ("NOT".equals(upper)
                    || "DEFAULT".equals(upper)
                    || "PRIMARY".equals(upper)
                    || "REFERENCES".equals(upper)
                    || "CHECK".equals(upper)
                    || "CONSTRAINT".equals(upper)
                    || "UNIQUE".equals(upper)) {
                break;
            }
            if (type.length() > 0) {
                type.append(' ');
            }
            type.append(token);
        }

        return type.toString().trim();
    }

    private String normalizeSchemaType(String schemaType) {
        String normalized = schemaType.trim().toUpperCase(Locale.ROOT);

        if (normalized.startsWith("BIGINT")) {
            return "bigint";
        }

        if (normalized.startsWith("VARCHAR") || normalized.startsWith("CHARACTER VARYING")) {
            return "character varying";
        }

        if (normalized.startsWith("TIMESTAMP WITH TIME ZONE")) {
            return "timestamp with time zone";
        }

        // Fallback: use lower-cased version without surrounding whitespace
        return normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeDatabaseType(String dataType, Integer charLength) {
        String dt = dataType == null ? "" : dataType.trim().toLowerCase(Locale.ROOT);

        if ("bigint".equals(dt)) {
            return "bigint";
        }

        if ("character varying".equals(dt) || "varchar".equals(dt)) {
            return "character varying";
        }

        if ("timestamp with time zone".equals(dt)) {
            return "timestamp with time zone";
        }

        return dt;
    }

    // -------------------------------------------------------------------------
    // Helper types
    // -------------------------------------------------------------------------

    private static final class TableDefinition {
        final String name;
        final String createTableSql;
        final Map<String, ColumnDefinition> columns = new LinkedHashMap<>();

        TableDefinition(String name, String createTableSql) {
            this.name = name;
            this.createTableSql = createTableSql;
        }
    }

    private static final class ColumnDefinition {
        final String name;
        final String fullDefinition;
        final String normalizedType;
        final boolean notNull;
        final boolean hasDefault;

        ColumnDefinition(String name,
                         String fullDefinition,
                         String normalizedType,
                         boolean notNull,
                         boolean hasDefault) {
            this.name = name;
            this.fullDefinition = fullDefinition;
            this.normalizedType = normalizedType;
            this.notNull = notNull;
            this.hasDefault = hasDefault;
        }

        boolean isCompatibleWith(ActualColumnInfo actual) {
            if (!normalizedType.equalsIgnoreCase(actual.normalizedType)) {
                return false;
            }

            // If canonical column is NOT NULL but database allows NULLs, treat as incompatible
            if (notNull && actual.nullable) {
                return false;
            }

            return true;
        }
    }

    private static final class ActualColumnInfo {
        final String name;
        final String normalizedType;
        final boolean nullable;

        ActualColumnInfo(String name, String normalizedType, boolean nullable) {
            this.name = name;
            this.normalizedType = normalizedType;
            this.nullable = nullable;
        }
    }
}
