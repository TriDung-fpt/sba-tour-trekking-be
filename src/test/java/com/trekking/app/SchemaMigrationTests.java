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
    void cooperationUpgradePreservesLegacyReceiptsAndRequiresExplicitCompleteTerms() throws Exception {
        String database = "terms_migration_" + UUID.randomUUID().toString().replace("-", "");
        assertThat(database).matches("[a-z0-9_]+");
        try (Connection admin = connection("auth_test")) {
            assertThat(value(admin, "select current_database()")).isEqualTo("auth_test");
            execute(admin, "create database " + database);
            try (Connection test = connection(database)) {
                execute(test, Files.readString(Path.of("schema/schema.sql")));
                ScriptUtils.executeSqlScript(test, new FileSystemResource("schema/migrations/001_add_google_subject.sql"));
                ScriptUtils.executeSqlScript(test, new FileSystemResource("schema/migrations/002_add_account_session_version.sql"));
                execute(test, "insert into trekking.app_accounts(email,password_hash,full_name) "
                        + "values ('legacy@example.invalid','preserved-hash','Legacy owner')");
                execute(test, "insert into trekking.companies(name) values ('Legacy company')");
                execute(test, "insert into trekking.policies(code,title,kind,created_by) values ('legacy','Legacy','cooperation',1)");
                execute(test, "insert into trekking.policy_versions(policy_id,version_number,content,effective_from,approved_by,approved_at) "
                        + "values (1,1,'Legacy terms',current_timestamp,1,current_timestamp)");
                execute(test, "insert into trekking.company_agreements(company_id,policy_version_id,accepted_by,accepted_at,email_verified_at,"
                        + "platform_fee_percent,payout_wait_hours,payout_terms_snapshot) values (1,1,1,current_timestamp,current_timestamp,8,96,'Legacy settlement')");
                // Execute the complete PostgreSQL script: Spring's semicolon parser does not parse dollar-quoted PL/pgSQL.
                execute(test, Files.readString(Path.of("schema/migrations/003_add_cooperation_policy_terms.sql")));
                assertThat(value(test, "select platform_fee_percent from trekking.policy_versions where id=1")).isNull();
                assertThat(value(test, "select payout_terms_snapshot from trekking.company_agreements where id=1")).isEqualTo("Legacy settlement");
                assertThat(value(test, "select payout_wait_hours from trekking.company_agreements where id=1")).isEqualTo(96);
                assertThatThrownBy(() -> execute(test, "update trekking.policy_versions set content='Changed' where id=1"))
                        .isInstanceOf(SQLException.class);
                assertThatThrownBy(() -> execute(test, "delete from trekking.company_agreements where id=1"))
                        .isInstanceOf(SQLException.class);
                String insert = "insert into trekking.policy_versions(policy_id,version_number,content,effective_from,platform_fee_percent,payout_wait_hours,payout_terms_snapshot) ";
                assertThatThrownBy(() -> execute(test, insert + "values (1,2,'New',current_timestamp,10,null,null)"))
                        .isInstanceOfSatisfying(SQLException.class, ex -> assertThat(ex.getSQLState()).isEqualTo("23514"));
                assertThatThrownBy(() -> execute(test, insert + "values (1,2,'New',current_timestamp,101,72,'Terms')"))
                        .isInstanceOfSatisfying(SQLException.class, ex -> assertThat(ex.getSQLState()).isEqualTo("23514"));
                execute(test, insert + "values (1,2,'New',current_timestamp,12.5,48,'Explicit terms')");
                assertThat(value(test, "select payout_wait_hours from trekking.policy_versions where version_number=2")).isEqualTo(48);
                assertThat(value(test, "select password_hash from trekking.app_accounts where id=1")).isEqualTo("preserved-hash");
            } finally {
                execute(admin, "drop database " + database);
            }
        }
    }

    @Test
    void sessionVersionUpgradePreservesLegacyAccountsAndRejectsNegativeVersions() throws Exception {
        String database = "session_migration_" + UUID.randomUUID().toString().replace("-", "");
        assertThat(database).matches("[a-z0-9_]+");
        try (Connection admin = connection("auth_test")) {
            assertThat(value(admin, "select current_database()")).isEqualTo("auth_test");
            execute(admin, "create database " + database);
            try (Connection test = connection(database)) {
                execute(test, Files.readString(Path.of("schema/schema.sql")));
                ScriptUtils.executeSqlScript(test, new FileSystemResource("schema/migrations/001_add_google_subject.sql"));
                execute(test, "insert into trekking.app_accounts(email,password_hash,full_name) "
                        + "values ('legacy@example.invalid','preserved-hash','Legacy')");
                ScriptUtils.executeSqlScript(test, new FileSystemResource("schema/migrations/002_add_account_session_version.sql"));
                assertThat(value(test, "select session_version from trekking.app_accounts where id=1")).isEqualTo(0L);
                assertThat(value(test, "select password_hash from trekking.app_accounts where id=1")).isEqualTo("preserved-hash");
                execute(test, "insert into trekking.app_accounts(email,password_hash,full_name) "
                        + "values ('new@example.invalid','new-hash','New')");
                assertThat(value(test, "select session_version from trekking.app_accounts where id=2")).isEqualTo(0L);
                assertThatThrownBy(() -> execute(test, "update trekking.app_accounts set session_version=-1 where id=1"))
                        .isInstanceOfSatisfying(SQLException.class, error -> assertThat(error.getSQLState()).isEqualTo("23514"));
            } finally {
                execute(admin, "drop database " + database);
            }
        }
    }

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
