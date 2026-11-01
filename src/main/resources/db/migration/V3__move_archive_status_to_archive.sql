ALTER TABLE interview_archives
    ADD COLUMN status       VARCHAR(10) NOT NULL DEFAULT 'VISIBLE' AFTER memo,
    ADD COLUMN like_count   INT         NOT NULL DEFAULT 0         AFTER status,
    ADD COLUMN report_count INT         NOT NULL DEFAULT 0         AFTER like_count;

ALTER TABLE archive_reports ADD COLUMN archive_id BIGINT NULL AFTER user_id;

UPDATE archive_reports r
    JOIN archive_questions q ON q.id = r.question_id
    SET r.archive_id = q.archive_id;

DELETE r1 FROM archive_reports r1
JOIN archive_reports r2
  ON r1.user_id = r2.user_id
 AND r1.archive_id = r2.archive_id
 AND r1.id > r2.id;

ALTER TABLE archive_reports DROP FOREIGN KEY fk_report_question;
ALTER TABLE archive_reports DROP INDEX uk_report_user_question;
ALTER TABLE archive_reports DROP COLUMN question_id;
ALTER TABLE archive_reports
    MODIFY archive_id BIGINT NOT NULL,
    ADD CONSTRAINT uk_report_user_archive UNIQUE (user_id, archive_id),
    ADD CONSTRAINT fk_report_archive FOREIGN KEY (archive_id) REFERENCES interview_archives (id);

UPDATE interview_archives a
    JOIN (SELECT archive_id,
    SUM(like_count)                 AS likes,
    MAX(status = 'HIDDEN')          AS hidden
    FROM archive_questions
    GROUP BY archive_id) q ON q.archive_id = a.id
    SET a.like_count = q.likes,
        a.status     = IF(q.hidden = 1, 'HIDDEN', 'VISIBLE');

UPDATE interview_archives a
SET a.report_count = (SELECT COUNT(*) FROM archive_reports r WHERE r.archive_id = a.id);

UPDATE interview_archives SET status = 'HIDDEN' WHERE report_count >= 3;

CREATE INDEX idx_archive_visible_latest  ON interview_archives (status, deleted_at, id);
CREATE INDEX idx_archive_visible_popular ON interview_archives (status, deleted_at, like_count, id);
CREATE INDEX idx_archive_company_visible ON interview_archives (company_id, status, id);

DROP INDEX idx_question_status_id ON archive_questions;
ALTER TABLE archive_questions
DROP COLUMN status,
    DROP COLUMN report_count;