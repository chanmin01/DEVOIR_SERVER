SET @has_feedbacks := (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'feedbacks'
);
SET @has_card_type := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'feedbacks' AND COLUMN_NAME = 'card_type'
);
SET @has_fb_session := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'feedbacks' AND COLUMN_NAME = 'session_id'
);
SET @has_fb_session_fk := (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'feedbacks' AND CONSTRAINT_NAME = 'fk_feedback_session'
);
SET @has_filler := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'session_feedbacks' AND COLUMN_NAME = 'filler_word_count'
);

-- 1. 카드 종류. 기존 행은 모두 질문 카드
SET @ddl := IF(@has_feedbacks > 0 AND @has_card_type = 0,
    'ALTER TABLE feedbacks ADD COLUMN card_type VARCHAR(20) NOT NULL DEFAULT ''QA''',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 발표 카드는 질문이 없으므로 세션을 직접 들고 있는다
SET @ddl := IF(@has_feedbacks > 0 AND @has_fb_session = 0,
    'ALTER TABLE feedbacks ADD COLUMN session_id BIGINT NULL',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(@has_feedbacks > 0,
    'UPDATE feedbacks f JOIN questions q ON q.id = f.question_id SET f.session_id = q.session_id WHERE f.session_id IS NULL',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(@has_feedbacks > 0,
    'ALTER TABLE feedbacks MODIFY COLUMN session_id BIGINT NOT NULL, MODIFY COLUMN question_id BIGINT NULL',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(@has_feedbacks > 0 AND @has_fb_session_fk = 0,
    'ALTER TABLE feedbacks ADD CONSTRAINT fk_feedback_session FOREIGN KEY (session_id) REFERENCES sessions (id)',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. 전달력 분석은 하지 않기로 해서 컬럼 정리
SET @ddl := IF(@has_filler > 0,
    'ALTER TABLE session_feedbacks DROP COLUMN filler_word_count, DROP COLUMN filler_word_note, DROP COLUMN speech_speed, DROP COLUMN speech_speed_note',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
