package com.benly.pt;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PtCompanyTaskMigrationTest {

    @Test
    void preservesExistingTaskAndSeedsThreeCompanyExercises() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:company-tasks-"
                        + UUID.randomUUID()
                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        );

        new ResourceDatabasePopulator(
                new ClassPathResource(
                        "db/migration/V2__create_archive_tables_and_seed_companies.sql"
                ),
                new ClassPathResource(
                        "db/migration/V6__pt_task_and_session.sql"
                )
        ).execute(dataSource);

        var jdbc = new JdbcTemplate(dataSource);

        Long samsungId = jdbc.queryForObject(
                """
                select id
                from companies
                where normalized_name = '삼성전자'
                """,
                Long.class
        );

        String oldContent = jdbc.queryForObject(
                """
                select content_json
                from pt_tasks
                where code = 'SME_SERVICE_V1'
                """,
                String.class
        );

        new ResourceDatabasePopulator(
                new ClassPathResource(
                        "db/migration/V14__pt_company_tasks_and_settings.sql"
                )
        ).execute(dataSource);

        assertEquals(
                4,
                jdbc.queryForObject(
                        "select count(*) from pt_tasks",
                        Integer.class
                )
        );

        assertEquals(
                oldContent,
                jdbc.queryForObject(
                        """
                        select content_json
                        from pt_tasks
                        where code = 'SME_SERVICE_V1'
                        """,
                        String.class
                )
        );

        assertEquals(
                "SECURITY,UI_UX,AI,DATA",
                jdbc.queryForObject(
                        """
                        select allowed_tracks
                        from pt_tasks
                        where code = 'SME_SERVICE_V1'
                        """,
                        String.class
                )
        );

        assertEquals(
                samsungId,
                jdbc.queryForObject(
                        """
                        select company_id
                        from pt_tasks
                        where code = 'SAMSUNG_SMARTHOME_SYNC_DEMO_V1'
                        """,
                        Long.class
                )
        );

        var mapper = new JsonMapper();

        var contents = jdbc.queryForList(
                """
                select content_json
                from pt_tasks
                where task_type = 'JOB_PROBLEM_SOLVING'
                """,
                String.class
        );

        assertEquals(3, contents.size());

        for (String content : contents) {
            var tree = mapper.readTree(content);

            assertTrue(
                    tree.path("exerciseAssumptions")
                            .toString()
                            .contains("연습용")
            );

            assertTrue(
                    tree.path("trackGuidance")
                            .isObject()
            );

            assertEquals(
                    0,
                    tree.path("trackGuidance")
                            .size()
            );

            assertTrue(
                    tree.path("presentationRequirements")
                            .size() >= 5
            );
        }

        assertEquals(
                3,
                jdbc.queryForObject(
                        """
                        select count(*)
                        from pt_tasks
                        where allowed_tracks = ''
                          and presentation_seconds = 300
                        """,
                        Integer.class
                )
        );
    }
}