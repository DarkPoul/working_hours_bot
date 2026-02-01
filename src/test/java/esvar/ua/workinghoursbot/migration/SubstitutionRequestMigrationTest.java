package esvar.ua.workinghoursbot.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class SubstitutionRequestMigrationTest {

    @Test
    void migrateTmUserIdTextToBigint() throws Exception {
        Path dbPath = Path.of("target", "tm_user_id_migration.db");
        Files.deleteIfExists(dbPath);
        String url = "jdbc:sqlite:" + dbPath.toAbsolutePath();

        Flyway.configure()
                .dataSource(url, "", "")
                .locations("classpath:db/migration")
                .mixed(true)
                .target("7")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(url);
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO locations (id, code, name, is_active, sort_order, schedule_edit_enabled)
                    VALUES ('loc-1', 'L1', 'Location 1', 1, 0, 0)
                    """);
            statement.execute("""
                    INSERT INTO user_accounts (
                        id,
                        telegram_user_id,
                        telegram_chat_id,
                        last_name,
                        role,
                        location_id,
                        status,
                        created_at,
                        updated_at
                    )
                    VALUES ('user-1', 123, 123, 'User', 'SELLER', 'loc-1', 'APPROVED', '2024-01-01', '2024-01-01')
                    """);
            statement.execute("""
                    INSERT INTO substitution_request (
                        id,
                        requester_user_id,
                        location_id,
                        request_date,
                        status,
                        urgent,
                        created_at,
                        version,
                        tm_user_id
                    )
                    VALUES ('req-1', 'user-1', 'loc-1', '2024-01-10', 'NEW', 0, '2024-01-01', 0, '12345')
                    """);
        }

        Flyway.configure()
                .dataSource(url, "", "")
                .locations("classpath:db/migration")
                .mixed(true)
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(url);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT tm_user_id FROM substitution_request WHERE id = 'req-1'"
             )) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getLong("tm_user_id")).isEqualTo(12345L);
        }
    }
}
