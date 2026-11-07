SET @has_feedbacks := (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'feedbacks'
);
SET @has_card_status := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'feedbacks' AND COLUMN_NAME = 'scoring_status'
);
SET @has_card_started := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'feedbacks' AND COLUMN_NAME = 'scoring_started_at'
);

-- 1. 카드별 채점 상태. 기존 카드는 모두 채점이 끝난 결과라 COMPLETED
SET @ddl := IF(@has_feedbacks > 0 AND @has_card_status = 0,
    'ALTER TABLE feedbacks ADD COLUMN scoring_status VARCHAR(20) NOT NULL DEFAULT ''COMPLETED''',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 카드 채점 시작 시각. 오래 멈춘 카드를 찾아 다시 채점하는 기준
SET @ddl := IF(@has_feedbacks > 0 AND @has_card_started = 0,
    'ALTER TABLE feedbacks ADD COLUMN scoring_started_at DATETIME(6) NULL',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
