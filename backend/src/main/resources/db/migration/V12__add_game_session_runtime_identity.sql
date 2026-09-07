ALTER TABLE game_sessions
    ADD COLUMN game_id CHAR(36) NULL AFTER id,
    ADD CONSTRAINT uk_game_sessions_game_id UNIQUE (game_id);
