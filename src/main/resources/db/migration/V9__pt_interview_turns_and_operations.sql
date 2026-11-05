ALTER TABLE pt_sessions ADD COLUMN retention_closed_at DATETIME(6) NULL;
ALTER TABLE pt_sessions ADD COLUMN operation_token VARCHAR(36) NULL;
ALTER TABLE pt_sessions ADD COLUMN operation_kind VARCHAR(30) NULL;
ALTER TABLE pt_sessions ADD COLUMN operation_started_at DATETIME(6) NULL;
ALTER TABLE pt_sessions ADD COLUMN presentation_payload_hash VARCHAR(64) NULL;
ALTER TABLE pt_sessions ADD COLUMN presentation_duration_sec INT NULL;

CREATE TABLE pt_question_turns (
                                   id BIGINT NOT NULL AUTO_INCREMENT,
                                   session_id BIGINT NOT NULL,
                                   question_id BIGINT NOT NULL,
                                   ordinal INT NOT NULL,
                                   answer_request_id VARCHAR(36) NULL,
                                   answer_payload_hash VARCHAR(64) NULL,
                                   PRIMARY KEY (id),
                                   CONSTRAINT uk_pt_turn_ordinal UNIQUE (session_id, ordinal),
                                   CONSTRAINT uk_pt_turn_question UNIQUE (question_id),
                                   CONSTRAINT uk_pt_turn_request UNIQUE (session_id, answer_request_id),
                                   CONSTRAINT fk_pt_turn_session FOREIGN KEY (session_id) REFERENCES pt_sessions(session_id),
                                   CONSTRAINT fk_pt_turn_question FOREIGN KEY (question_id) REFERENCES questions(id)
);

CREATE TABLE pt_audio_receipts (
                                   id BIGINT NOT NULL AUTO_INCREMENT,
                                   session_id BIGINT NOT NULL,
                                   request_id VARCHAR(36) NOT NULL,
                                   question_id BIGINT NULL,
                                   payload_hash VARCHAR(64) NOT NULL,
                                   admitted_at DATETIME(6) NOT NULL,
                                   PRIMARY KEY (id),
                                   CONSTRAINT uk_pt_audio_request UNIQUE (session_id, request_id),
                                   CONSTRAINT fk_pt_audio_session FOREIGN KEY (session_id) REFERENCES pt_sessions(session_id)
);

CREATE INDEX idx_pt_retention ON pt_sessions(expires_at, retention_closed_at);