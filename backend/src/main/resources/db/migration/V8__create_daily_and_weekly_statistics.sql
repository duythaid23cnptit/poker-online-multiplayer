CREATE TABLE daily_statistics (
    user_id BIGINT NOT NULL, stat_date DATE NOT NULL,
    hands_played BIGINT NOT NULL DEFAULT 0, hands_won BIGINT NOT NULL DEFAULT 0,
    hands_lost BIGINT NOT NULL DEFAULT 0, hands_tied BIGINT NOT NULL DEFAULT 0,
    chips_won BIGINT NOT NULL DEFAULT 0, chips_lost BIGINT NOT NULL DEFAULT 0,
    net_chips BIGINT NOT NULL DEFAULT 0, largest_pot_won BIGINT NOT NULL DEFAULT 0,
    playing_time_seconds BIGINT NOT NULL DEFAULT 0, sessions_participated BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_daily_statistics PRIMARY KEY (user_id, stat_date),
    CONSTRAINT fk_daily_statistics_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_daily_statistics_counts CHECK (hands_played >= 0 AND hands_won >= 0 AND hands_lost >= 0 AND hands_tied >= 0),
    CONSTRAINT chk_daily_statistics_chips CHECK (chips_won >= 0 AND chips_lost >= 0 AND largest_pot_won >= 0),
    CONSTRAINT chk_daily_statistics_time_sessions CHECK (playing_time_seconds >= 0 AND sessions_participated >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE weekly_statistics (
    user_id BIGINT NOT NULL, week_start_date DATE NOT NULL,
    hands_played BIGINT NOT NULL DEFAULT 0, hands_won BIGINT NOT NULL DEFAULT 0,
    hands_lost BIGINT NOT NULL DEFAULT 0, hands_tied BIGINT NOT NULL DEFAULT 0,
    chips_won BIGINT NOT NULL DEFAULT 0, chips_lost BIGINT NOT NULL DEFAULT 0,
    net_chips BIGINT NOT NULL DEFAULT 0, largest_pot_won BIGINT NOT NULL DEFAULT 0,
    playing_time_seconds BIGINT NOT NULL DEFAULT 0, sessions_participated BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_weekly_statistics PRIMARY KEY (user_id, week_start_date),
    CONSTRAINT fk_weekly_statistics_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_weekly_statistics_counts CHECK (hands_played >= 0 AND hands_won >= 0 AND hands_lost >= 0 AND hands_tied >= 0),
    CONSTRAINT chk_weekly_statistics_chips CHECK (chips_won >= 0 AND chips_lost >= 0 AND largest_pot_won >= 0),
    CONSTRAINT chk_weekly_statistics_time_sessions CHECK (playing_time_seconds >= 0 AND sessions_participated >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
