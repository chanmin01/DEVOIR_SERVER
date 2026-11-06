package com.benly.pt;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.UUID;

class PtCombinedMigrationTest {
    @Test
    void migrationPreservesRowsAndEnforcesOrdinalQuestionAndRequestUniqueness() {
        var ds =
                new DriverManagerDataSource(
                        "jdbc:h2:mem:ptV9" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                        "sa",
                        "");
        var jdbc = new JdbcTemplate(ds);
        jdbc.execute(
                "CREATE TABLE pt_sessions(session_id BIGINT PRIMARY KEY,expires_at DATETIME(6))");
        jdbc.execute("CREATE TABLE questions(id BIGINT PRIMARY KEY)");
        jdbc.update("INSERT INTO pt_sessions VALUES(10,NULL),(11,NULL)");
        jdbc.update("INSERT INTO questions VALUES(1),(2),(3),(4)");
        new ResourceDatabasePopulator(
                new ClassPathResource(
                        "db/migration/V9__pt_interview_turns_and_operations.sql"))
                .execute(ds);
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM pt_sessions", Integer.class));
        assertNull(
                jdbc.queryForObject(
                        "SELECT operation_token FROM pt_sessions WHERE session_id=10",
                        String.class));
        jdbc.update(
                "INSERT INTO pt_question_turns(session_id,question_id,ordinal,answer_request_id)"
                        + " VALUES(10,1,1,'request-one')");
        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        jdbc.update(
                                "INSERT INTO pt_question_turns(session_id,question_id,ordinal)"
                                        + " VALUES(10,2,1)"));
        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        jdbc.update(
                                "INSERT INTO pt_question_turns(session_id,question_id,ordinal)"
                                        + " VALUES(11,1,1)"));
        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        jdbc.update(
                                "INSERT INTO"
                                        + " pt_question_turns(session_id,question_id,ordinal,answer_request_id)"
                                        + " VALUES(10,2,2,'request-one')"));
        jdbc.update(
                "INSERT INTO pt_question_turns(session_id,question_id,ordinal,answer_request_id)"
                        + " VALUES(11,2,1,'request-one')");
        jdbc.update(
                "INSERT INTO pt_audio_receipts(session_id,request_id,payload_hash,admitted_at)"
                        + " VALUES(10,'receipt','hash',CURRENT_TIMESTAMP)");
        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        jdbc.update(
                                "INSERT INTO"
                                        + " pt_audio_receipts(session_id,request_id,payload_hash,admitted_at)"
                                        + " VALUES(10,'receipt','other',CURRENT_TIMESTAMP)"));
    }
}