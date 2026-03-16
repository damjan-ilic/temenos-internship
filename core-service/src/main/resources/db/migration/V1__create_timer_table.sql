CREATE TABLE timer (
                       timer_id UUID PRIMARY KEY,
                       created  BIGINT NOT NULL,
                       delay    INT NOT NULL
);