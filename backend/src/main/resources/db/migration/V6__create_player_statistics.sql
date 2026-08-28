CREATE TABLE player_statistics (
    user_id BIGINT NOT NULL,
    total_games BIGINT NOT NULL DEFAULT 0,
    total_hands BIGINT NOT NULL DEFAULT 0,
    total_wins BIGINT NOT NULL DEFAULT 0,
    total_losses BIGINT NOT NULL DEFAULT 0,
    win_rate DECIMAL(7,2) NOT NULL DEFAULT 0.00,
    total_chips_won BIGINT NOT NULL DEFAULT 0,
    total_chips_lost BIGINT NOT NULL DEFAULT 0,
    net_chip BIGINT NOT NULL DEFAULT 0,
    largest_pot_won BIGINT NOT NULL DEFAULT 0,
    average_playing_seconds BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_player_statistics PRIMARY KEY (user_id),
    CONSTRAINT fk_player_statistics_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_player_statistics_counts CHECK (
        total_games >= 0 AND total_hands >= 0 AND total_wins >= 0 AND total_losses >= 0
    ),
    CONSTRAINT chk_player_statistics_win_rate CHECK (win_rate >= 0 AND win_rate <= 100),
    CONSTRAINT chk_player_statistics_chips CHECK (
        total_chips_won >= 0 AND total_chips_lost >= 0 AND largest_pot_won >= 0
    ),
    CONSTRAINT chk_player_statistics_playing_time CHECK (average_playing_seconds >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
