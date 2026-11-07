CREATE INDEX idx_archive_user_latest ON interview_archives (user_id, deleted_at, id);
CREATE INDEX idx_like_user_latest    ON archive_likes (user_id, id);
