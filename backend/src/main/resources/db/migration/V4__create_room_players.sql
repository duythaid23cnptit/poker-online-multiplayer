CREATE TABLE room_players (
    id BIGINT NOT NULL AUTO_INCREMENT,
    room_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    seat_number INT NULL,
    player_state VARCHAR(20) NOT NULL,
    table_chips BIGINT NOT NULL DEFAULT 0,
    joined_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    left_at DATETIME(6) NULL,
    CONSTRAINT pk_room_players PRIMARY KEY (id),
    CONSTRAINT uk_room_players_room_user UNIQUE (room_id, user_id),
    CONSTRAINT uk_room_players_room_seat UNIQUE (room_id, seat_number),
    CONSTRAINT fk_room_players_room FOREIGN KEY (room_id) REFERENCES rooms (id) ON DELETE CASCADE,
    CONSTRAINT fk_room_players_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_room_players_seat_number CHECK (seat_number IS NULL OR seat_number BETWEEN 1 AND 9),
    CONSTRAINT chk_room_players_state CHECK (
        player_state IN ('NOT_READY', 'READY', 'PLAYING', 'SPECTATING', 'DISCONNECTED', 'LEAVING')
    ),
    CONSTRAINT chk_room_players_table_chips_non_negative CHECK (table_chips >= 0),
    INDEX idx_room_players_user_id (user_id),
    INDEX idx_room_players_room_state (room_id, player_state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
