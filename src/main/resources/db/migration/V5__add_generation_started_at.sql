ALTER TABLE sessions
    ADD COLUMN generation_started_at DATETIME(6) NULL AFTER status;