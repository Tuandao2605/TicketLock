package com.ticketlock.migration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import static org.junit.jupiter.api.Assertions.*;

/** Each test uses a generated schema in the dedicated test database. */
@SpringBootTest(properties = "spring.session.data.redis.namespace=ticketlock:test:mockmvc")
class MigrationIntegrationTests {
    @Value("${spring.datasource.url}") String url;
    @Value("${spring.datasource.username}") String username;
    @Value("${spring.datasource.password}") String password;
    DataSource dataSource;

    @BeforeEach
    void isolatedConnections() {
        dataSource = new DriverManagerDataSource(url, username, password);
    }

    @Test
    void freshMigrationCanRunTwice() throws Exception {
        String schema = schema();
        try {
            var flyway = flyway(schema, "classpath:db/migration");
            assertEquals(5, flyway.migrate().migrationsExecuted);
            assertEquals(0, flyway.migrate().migrationsExecuted);
            assertEquals(5, flyway.info().applied().length);
            try (Connection c = dataSource.getConnection(); Statement s = c.createStatement();
                 var rs = s.executeQuery("SELECT count(*) FROM " + schema + ".app_users")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1));
            }
        } finally { drop(schema); }
    }

    @Test
    void baselinePreservesLegacyUserAndRefreshToken() throws Exception {
        String schema = schema();
        try {
            try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
                c.setSchema(schema);
                ScriptUtils.executeSqlScript(c, new ClassPathResource("db/migration/V1__mini_schema.sql"));
                s.executeUpdate("INSERT INTO app_users VALUES ('01a1c334-1054-769f-93dc-d7056cbfc21d', 'fixture@example.test', 'fake-hash', 'CUSTOMER', true, '2025-01-01T00:00:00Z')");
                s.executeUpdate("INSERT INTO refresh_tokens (token_hash, family_id, user_id, created_at, expires_at) VALUES (repeat('a',64), '22222222-2222-4222-8222-222222222222', '01a1c334-1054-769f-93dc-d7056cbfc21d', '2025-01-01T00:00:00Z', '2030-01-01T00:00:00Z')");
            }
            var flyway = flyway(schema, "classpath:db/migration");
            flyway.baseline();
            assertEquals(4, flyway.migrate().migrationsExecuted);
            try (Connection c = dataSource.getConnection(); Statement s = c.createStatement();
                 var rs = s.executeQuery("SELECT u.id::text,u.password_hash,u.role,u.enabled,r.user_id::text,r.token_hash FROM " + schema + ".app_users u JOIN " + schema + ".refresh_tokens r ON r.user_id=u.id")) {
                assertTrue(rs.next());
                assertEquals("01a1c334-1054-769f-93dc-d7056cbfc21d", rs.getString(1));
                assertEquals("fake-hash", rs.getString(2));
                assertEquals("CUSTOMER", rs.getString(3));
                assertTrue(rs.getBoolean(4));
                assertEquals(rs.getString(1), rs.getString(5));
                assertEquals("a".repeat(64), rs.getString(6));
                assertFalse(rs.next());
            }
        } finally { drop(schema); }
    }

    @Test
    void malformedMigrationFails(@TempDir Path directory) throws Exception {
        String schema = schema();
        try {
            Files.writeString(directory.resolve("V1__invalid.sql"), "THIS IS NOT VALID SQL;");
            var flyway = flyway(schema, "filesystem:" + directory.toAbsolutePath());
            assertThrows(FlywayException.class, flyway::migrate);
        } finally { drop(schema); }
    }

    private Flyway flyway(String schema, String location) {
        return Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations(location).load();
    }

    private String schema() throws Exception {
        String name = "adv004_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE SCHEMA " + name);
        }
        return name;
    }

    private void drop(String schema) throws Exception {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }
}
