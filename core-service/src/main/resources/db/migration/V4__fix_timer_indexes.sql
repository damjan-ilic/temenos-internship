DROP INDEX idx_pending;
DROP INDEX idx_scheduled;

CREATE INDEX idx_pending   ON timer ((created_at + delay * 1000)) WHERE status = 'PENDING';
CREATE INDEX idx_scheduled ON timer ((created_at + delay * 1000)) WHERE status = 'SCHEDULED';