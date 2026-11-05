package com.benly.pt;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.UUID;

class PtProgressMigrationTest {
    @Test
    void addsNullablePresentationColumnsWithoutLosingOldRows() {
        var ds =
                new DriverManagerDataSource(
                        "jdbc:h2:mem:pt2migration"
                                + UUID.randomUUID()
                                + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                        "sa",
                        "");
        new ResourceDatabasePopulator(
                new ClassPathResource(
                        "db/migration/V2__create_archive_tables_and_seed_companies.sql"),
                new ClassPathResource("db/migration/V6__pt_task_and_session.sql"))
                .execute(ds);
        var jdbc = new JdbcTemplate(ds);
        Long task = jdbc.queryForObject("select id from pt_tasks", Long.class);
        jdbc.update(
                "insert into"
                        + " pt_sessions(session_id,task_id,creation_request_id,phase,preparation_minutes,preparation_started_at,preparation_deadline)"
                        + " values(100,?,?,'PREPARING',20,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                task,
                UUID.randomUUID().toString());
        new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V7__pt_presentation_fields.sql"))
                .execute(ds);
        assertEquals(
                1,
                jdbc.queryForObject(
                        "select count(*) from pt_sessions where session_id=100", Integer.class));
        assertEquals(
                "PREPARING",
                jdbc.queryForObject(
                        "select phase from pt_sessions where session_id=100", String.class));
        var row =
                jdbc.queryForMap(
                        "select"
                                + " track,presentation_started_at,presentation_deadline,presentation_transcript,presentation_input_type,presentation_request_id,presentation_submitted_at"
                                + " from pt_sessions where session_id=100");
        assertEquals(7, row.size());
        assertTrue(row.values().stream().allMatch(v -> v == null));
    }
}
