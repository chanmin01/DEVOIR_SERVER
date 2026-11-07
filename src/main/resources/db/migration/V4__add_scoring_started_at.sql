ALTER TABLE session_feedbacks
    ADD COLUMN scoring_started_at DATETIME(6) NULL AFTER scoring_status;

UPDATE session_feedbacks
SET scoring_started_at = COALESCE(updated_at, created_at)
WHERE scoring_status = 'SCORING';
