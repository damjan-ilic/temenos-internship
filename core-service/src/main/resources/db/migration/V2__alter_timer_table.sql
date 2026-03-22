ALTER TABLE timer
    RENAME COLUMN created TO created_at;

ALTER TABLE timer
    ADD COLUMN status     VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN attempts   INT         NOT NULL DEFAULT 0,
    ADD COLUMN updated_at BIGINT NOT NULL DEFAULT extract(epoch from now());

CREATE INDEX idx_pending    ON timer ((created_at + delay)) WHERE status = 'PENDING';
CREATE INDEX idx_failed     ON timer (attempts)           WHERE status = 'FAILED';
CREATE INDEX idx_processing ON timer (updated_at)         WHERE status = 'PROCESSING';
CREATE INDEX idx_scheduled  ON timer ((created_at + delay)) WHERE status = 'SCHEDULED';