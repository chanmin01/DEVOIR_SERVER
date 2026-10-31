ALTER TABLE interview_archives
    ADD COLUMN status       VARCHAR(10) NOT NULL DEFAULT 'VISIBLE' AFTER memo,
    ADD COLUMN like_count   INT         NOT NULL DEFAULT 0         AFTER status,
    ADD COLUMN report_count INT         NOT NULL DEFAULT 0         AFTER like_count;

UPDATE interview_archives a
SET a.like_count = (SELECT COALESCE(SUM(q.like_count), 0)
                    FROM archive_questions q
                    WHERE q.archive_id = a.id);

CREATE INDEX idx_archive_visible_latest  ON interview_archives (status, deleted_at, id);
CREATE INDEX idx_archive_visible_popular ON interview_archives (status, deleted_at, like_count, id);
CREATE INDEX idx_archive_company_visible ON interview_archives (company_id, status, id);

DROP INDEX idx_question_status_id ON archive_questions;
ALTER TABLE archive_questions
DROP COLUMN status,
    DROP COLUMN report_count;

DROP TABLE IF EXISTS archive_reports;
CREATE TABLE archive_reports (
                                 id         BIGINT      NOT NULL AUTO_INCREMENT,
                                 user_id    BIGINT      NOT NULL,
                                 archive_id BIGINT      NOT NULL,
                                 reason     VARCHAR(20) NOT NULL,
                                 created_at DATETIME(6) NOT NULL,
                                 PRIMARY KEY (id),
                                 CONSTRAINT uk_report_user_archive UNIQUE (user_id, archive_id),
                                 CONSTRAINT fk_report_archive FOREIGN KEY (archive_id) REFERENCES interview_archives (id)
);