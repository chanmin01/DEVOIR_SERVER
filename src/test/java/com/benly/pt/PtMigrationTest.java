package com.benly.pt;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PtMigrationTest {

    @Test
    void migrationReusesExistingCompanyAliasAndStoresSample() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:migration"
                        + UUID.randomUUID()
                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        );

        new ResourceDatabasePopulator(
                new ClassPathResource(
                        "db/migration/V2__create_archive_tables_and_seed_companies.sql"
                )
        ).execute(dataSource);

        var jdbc = new JdbcTemplate(dataSource);

        jdbc.update("""
            insert into companies(
                name,
                normalized_name,
                source,
                created_at
            )
            values(
                'IBK기업은행',
                'ibk기업은행',
                'USER',
                CURRENT_TIMESTAMP
            )
            """);

        Long existingCompanyId = jdbc.queryForObject(
                """
                select id
                from companies
                where normalized_name = 'ibk기업은행'
                """,
                Long.class
        );

        jdbc.update(
                """
                insert into company_aliases(
                    company_id,
                    alias,
                    normalized_alias
                )
                values (?, '기업은행', '기업은행')
                """,
                existingCompanyId
        );

        new ResourceDatabasePopulator(
                new ClassPathResource(
                        "db/migration/V6__pt_task_and_session.sql"
                )
        ).execute(dataSource);

        assertEquals(
                existingCompanyId,
                jdbc.queryForObject(
                        "select company_id from pt_tasks",
                        Long.class
                )
        );

        assertEquals(
                0,
                jdbc.queryForObject(
                        """
                        select count(*)
                        from companies
                        where normalized_name = '기업은행'
                        """,
                        Integer.class
                )
        );

        assertEquals(
                "APPROVED",
                jdbc.queryForObject(
                        "select status from pt_tasks",
                        String.class
                )
        );

        assertTrue(
                jdbc.queryForObject(
                        "select content_json from pt_tasks",
                        String.class
                ).contains("가상")
        );

        assertEquals(
                0,
                jdbc.queryForObject(
                        """
                        select count(*)
                        from information_schema.columns
                        where table_name = 'PT_SESSIONS'
                          and column_name = 'TASK_SNAPSHOT'
                        """,
                        Integer.class
                )
        );

        assertEquals(
                1,
                jdbc.queryForObject(
                        """
                        select count(*)
                        from information_schema.columns
                        where table_name = 'PT_SESSIONS'
                          and column_name = 'TASK_ID'
                        """,
                        Integer.class
                )
        );
    }
}