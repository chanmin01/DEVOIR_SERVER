package com.benly.pt;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PtCanceledPhaseMigrationTest {

    @Test
    void backfillsOnlyCanceledParentsAndCanRunAgain() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:cancelMigration"
                        + UUID.randomUUID()
                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        );

        var jdbc = new JdbcTemplate(dataSource);

        jdbc.execute("""
                CREATE TABLE sessions (
                    id BIGINT PRIMARY KEY,
                    status VARCHAR(30)
                )
                """);

        jdbc.execute("""
                CREATE TABLE pt_sessions (
                    session_id BIGINT PRIMARY KEY,
                    phase VARCHAR(30)
                )
                """);

        jdbc.update("""
                INSERT INTO sessions
                VALUES
                    (1, 'CANCELED'),
                    (2, 'IN_PROGRESS'),
                    (3, 'CANCELED')
                """);

        jdbc.update("""
                INSERT INTO pt_sessions
                VALUES
                    (1, 'PRESENTING'),
                    (2, 'GENERATING'),
                    (3, 'CANCELED')
                """);

        var script = new ResourceDatabasePopulator(
                new ClassPathResource(
                        "db/migration/V8__sync_canceled_pt_sessions.sql"
                )
        );

        script.execute(dataSource);
        script.execute(dataSource);

        assertEquals(
                "CANCELED",
                jdbc.queryForObject(
                        """
                        SELECT phase
                        FROM pt_sessions
                        WHERE session_id = 1
                        """,
                        String.class
                )
        );

        assertEquals(
                "GENERATING",
                jdbc.queryForObject(
                        """
                        SELECT phase
                        FROM pt_sessions
                        WHERE session_id = 2
                        """,
                        String.class
                )
        );

        assertEquals(
                "CANCELED",
                jdbc.queryForObject(
                        """
                        SELECT phase
                        FROM pt_sessions
                        WHERE session_id = 3
                        """,
                        String.class
                )
        );
    }
}