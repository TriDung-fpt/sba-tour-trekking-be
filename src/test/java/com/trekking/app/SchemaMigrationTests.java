package com.trekking.app;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import static org.assertj.core.api.Assertions.*;

class SchemaMigrationTests extends AuthTestInfrastructure {
    @Test
    void googleSubjectMigrationPreservesPasswordAccountsAndEnforcesUniqueNonblankSubject() throws Exception {
        String database = "google_migration_" + UUID.randomUUID().toString().replace("-", "");
        assertThat(database).matches("[a-z0-9_]+");
        try (Connection admin = connection("auth_test")) {
            assertThat(value(admin, "select current_database()")).isEqualTo("auth_test");
            execute(admin, "create database " + database);
            try (Connection test = connection(database)) {
                assertThat(value(test, "select current_database()")).isEqualTo(database);
                execute(test, Files.readString(Path.of("schema/schema.sql")));
                execute(test, "insert into trekking.app_accounts(email, password_hash, full_name, email_verified_at) "
                        + "values (' LEGACY@EXAMPLE.INVALID ', 'existing-password-hash', 'Existing Customer', CURRENT_TIMESTAMP)");
                execute(test, "insert into trekking.account_roles(account_id, role) values (1, 'customer')");
                ScriptUtils.executeSqlScript(test, new FileSystemResource("schema/migrations/001_add_google_subject.sql"));
                assertThat(value(test, "select email from trekking.app_accounts where id=1")).isEqualTo(" LEGACY@EXAMPLE.INVALID ");
                assertThat(value(test, "select password_hash from trekking.app_accounts where id=1")).isEqualTo("existing-password-hash");
                assertThat(value(test, "select google_subject from trekking.app_accounts where id=1")).isNull();
                assertThat(value(test, "select role from trekking.account_roles where account_id=1")).isEqualTo("customer");
                execute(test, "insert into trekking.app_accounts(email, password_hash, full_name) "
                        + "values ('second@example.invalid', 'second-password-hash', 'Second Customer')");
                assertThat(value(test, "select count(*) from trekking.app_accounts where google_subject is null")).isEqualTo(2L);
                execute(test, "update trekking.app_accounts set google_subject='google-subject-123' where id=1");
                assertThatThrownBy(() -> execute(test, "update trekking.app_accounts set google_subject='google-subject-123' where id=2"))
                        .isInstanceOfSatisfying(SQLException.class, error -> assertThat(error.getSQLState()).isEqualTo("23505"));
                assertThatThrownBy(() -> execute(test, "update trekking.app_accounts set google_subject='   ' where id=2"))
                        .isInstanceOfSatisfying(SQLException.class, error -> assertThat(error.getSQLState()).isEqualTo("23514"));
                assertThat(value(test, "select google_subject from trekking.app_accounts where id=2")).isNull();
                assertThat(value(test, "select count(*) from trekking.app_accounts")).isEqualTo(2L);
            } finally {
                // Only this randomly named database on the disposable Testcontainers server is removed.
                assertThat(value(admin, "select current_database()")).isEqualTo("auth_test");
                execute(admin, "drop database " + database);
            }
        }
    }

    private Connection connection(String database) throws SQLException {
        return DriverManager.getConnection("jdbc:postgresql://" + POSTGRES.getHost() + ":"
                + POSTGRES.getMappedPort(5432) + "/" + database, "auth_test", "auth_test");
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) { statement.execute(sql); }
    }

    private Object value(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            assertThat(rows.next()).isTrue();
            return rows.getObject(1);
        }
    }
}
