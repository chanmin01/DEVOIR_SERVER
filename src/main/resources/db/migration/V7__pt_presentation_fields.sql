ALTER TABLE pt_sessions
    ADD COLUMN track VARCHAR(20) NULL;

ALTER TABLE pt_sessions
    ADD COLUMN presentation_started_at DATETIME(6) NULL;

ALTER TABLE pt_sessions
    ADD COLUMN presentation_deadline DATETIME(6) NULL;

ALTER TABLE pt_sessions
    ADD COLUMN presentation_transcript MEDIUMTEXT NULL;

ALTER TABLE pt_sessions
    ADD COLUMN presentation_input_type VARCHAR(10) NULL;

ALTER TABLE pt_sessions
    ADD COLUMN presentation_request_id VARCHAR(36) NULL;

ALTER TABLE pt_sessions
    ADD COLUMN presentation_submitted_at DATETIME(6) NULL;