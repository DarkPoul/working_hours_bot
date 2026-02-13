package esvar.ua.workinghoursbot.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class SimplifiedDomainMigrationTest {

    @Test
    void flywayMigratesToLatestVersion() throws Exception {
        Path db = Path.of("target", "simplified_domain_migration.db");
        Files.deleteIfExists(db);

        Flyway flyway = Flyway.configure()
                .dataSource("jdbc:sqlite:" + db.toAbsolutePath(), "", "")
                .locations("classpath:db/migration")
                .mixed(true)
                .load();

        flyway.migrate();
        assertEquals("10", flyway.info().current().getVersion().getVersion());
    }
}
