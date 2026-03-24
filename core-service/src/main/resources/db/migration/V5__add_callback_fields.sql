ALTER TABLE timer
    ADD COLUMN callback_url VARCHAR(500),
    ADD COLUMN csrf_token   VARCHAR(255);