CREATE TABLE game_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    room_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_at DATETIME(6) NOT NULL,
    ended_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_game_sessions PRIMARY KEY (id),
    CONSTRAINT fk_game_sessions_room FOREIGN KEY (room_id) REFERENCES rooms (id) ON DELETE RESTRICT,
    CONSTRAINT chk_game_sessions_status CHECK (status IN ('ACTIVE', 'FINISHED', 'ABORTED')),
    CONSTRAINT chk_game_sessions_time_order CHECK (ended_at IS NULL OR ended_at >= started_at),
    INDEX idx_game_sessions_room_id (room_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE poker_hands (
    id BIGINT NOT NULL AUTO_INCREMENT,
    game_session_id BIGINT NOT NULL,
    hand_number BIGINT NOT NULL,
    dealer_seat INT NOT NULL,
    small_blind_seat INT NOT NULL,
    big_blind_seat INT NOT NULL,
    small_blind_amount BIGINT NOT NULL,
    big_blind_amount BIGINT NOT NULL,
    started_at DATETIME(6) NOT NULL,
    ended_at DATETIME(6) NULL,
    final_phase VARCHAR(20) NOT NULL,
    board_cards VARCHAR(32) NOT NULL DEFAULT '',
    end_reason VARCHAR(30) NULL,
    CONSTRAINT pk_poker_hands PRIMARY KEY (id),
    CONSTRAINT uk_poker_hands_session_number UNIQUE (game_session_id, hand_number),
    CONSTRAINT fk_poker_hands_session FOREIGN KEY (game_session_id) REFERENCES game_sessions (id) ON DELETE CASCADE,
    CONSTRAINT chk_poker_hands_number_positive CHECK (hand_number > 0),
    CONSTRAINT chk_poker_hands_dealer_seat CHECK (dealer_seat BETWEEN 1 AND 9),
    CONSTRAINT chk_poker_hands_small_blind_seat CHECK (small_blind_seat BETWEEN 1 AND 9),
    CONSTRAINT chk_poker_hands_big_blind_seat CHECK (big_blind_seat BETWEEN 1 AND 9),
    CONSTRAINT chk_poker_hands_small_blind_positive CHECK (small_blind_amount > 0),
    CONSTRAINT chk_poker_hands_big_blind_greater CHECK (big_blind_amount > small_blind_amount),
    CONSTRAINT chk_poker_hands_final_phase CHECK (
        final_phase IN ('PRE_FLOP', 'FLOP', 'TURN', 'RIVER', 'SHOWDOWN', 'FINISHED')
    ),
    CONSTRAINT chk_poker_hands_end_reason CHECK (
        end_reason IS NULL OR end_reason IN ('SHOWDOWN', 'ALL_OTHERS_FOLDED', 'ABORTED')
    ),
    CONSTRAINT chk_poker_hands_time_order CHECK (ended_at IS NULL OR ended_at >= started_at),
    INDEX idx_poker_hands_session_id (game_session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE hand_players (
    id BIGINT NOT NULL AUTO_INCREMENT,
    poker_hand_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    seat_number INT NOT NULL,
    starting_table_chips BIGINT NOT NULL,
    ending_table_chips BIGINT NOT NULL,
    total_committed BIGINT NOT NULL,
    participation_state VARCHAR(20) NOT NULL,
    connected_at_end BOOLEAN NOT NULL,
    leaving_at_end BOOLEAN NOT NULL,
    hole_cards VARCHAR(8) NULL,
    CONSTRAINT pk_hand_players PRIMARY KEY (id),
    CONSTRAINT uk_hand_players_hand_user UNIQUE (poker_hand_id, user_id),
    CONSTRAINT uk_hand_players_hand_seat UNIQUE (poker_hand_id, seat_number),
    CONSTRAINT fk_hand_players_hand FOREIGN KEY (poker_hand_id) REFERENCES poker_hands (id) ON DELETE CASCADE,
    CONSTRAINT fk_hand_players_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_hand_players_seat CHECK (seat_number BETWEEN 1 AND 9),
    CONSTRAINT chk_hand_players_starting_chips CHECK (starting_table_chips >= 0),
    CONSTRAINT chk_hand_players_ending_chips CHECK (ending_table_chips >= 0),
    CONSTRAINT chk_hand_players_committed CHECK (total_committed >= 0),
    CONSTRAINT chk_hand_players_participation CHECK (participation_state IN ('ACTIVE', 'FOLDED', 'ALL_IN')),
    CONSTRAINT chk_hand_players_leaving_folded CHECK (leaving_at_end = FALSE OR participation_state = 'FOLDED'),
    INDEX idx_hand_players_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE player_actions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    poker_hand_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    action_sequence BIGINT NOT NULL,
    phase VARCHAR(20) NOT NULL,
    action_type VARCHAR(20) NOT NULL,
    amount_committed_by_action BIGINT NOT NULL,
    resulting_player_current_bet BIGINT NOT NULL,
    resulting_game_current_bet BIGINT NOT NULL,
    resulting_table_chips BIGINT NOT NULL,
    turn_id VARCHAR(36) NULL,
    client_action_id VARCHAR(36) NULL,
    acted_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_player_actions PRIMARY KEY (id),
    CONSTRAINT uk_player_actions_hand_sequence UNIQUE (poker_hand_id, action_sequence),
    CONSTRAINT fk_player_actions_hand FOREIGN KEY (poker_hand_id) REFERENCES poker_hands (id) ON DELETE CASCADE,
    CONSTRAINT fk_player_actions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_player_actions_sequence_positive CHECK (action_sequence > 0),
    CONSTRAINT chk_player_actions_phase CHECK (phase IN ('PRE_FLOP', 'FLOP', 'TURN', 'RIVER')),
    CONSTRAINT chk_player_actions_type CHECK (action_type IN ('FOLD', 'CHECK', 'CALL', 'BET', 'RAISE', 'ALL_IN')),
    CONSTRAINT chk_player_actions_amount CHECK (amount_committed_by_action >= 0),
    CONSTRAINT chk_player_actions_player_bet CHECK (resulting_player_current_bet >= 0),
    CONSTRAINT chk_player_actions_game_bet CHECK (resulting_game_current_bet >= 0),
    CONSTRAINT chk_player_actions_table_chips CHECK (resulting_table_chips >= 0),
    INDEX idx_player_actions_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE pots (
    id BIGINT NOT NULL AUTO_INCREMENT,
    poker_hand_id BIGINT NOT NULL,
    pot_index INT NOT NULL,
    pot_type VARCHAR(10) NOT NULL,
    amount BIGINT NOT NULL,
    contribution_cap BIGINT NOT NULL,
    CONSTRAINT pk_pots PRIMARY KEY (id),
    CONSTRAINT uk_pots_hand_index UNIQUE (poker_hand_id, pot_index),
    CONSTRAINT fk_pots_hand FOREIGN KEY (poker_hand_id) REFERENCES poker_hands (id) ON DELETE CASCADE,
    CONSTRAINT chk_pots_index CHECK (pot_index >= 0),
    CONSTRAINT chk_pots_type CHECK (pot_type IN ('MAIN', 'SIDE')),
    CONSTRAINT chk_pots_type_index CHECK (
        (pot_type = 'MAIN' AND pot_index = 0) OR (pot_type = 'SIDE' AND pot_index > 0)
    ),
    CONSTRAINT chk_pots_amount CHECK (amount > 0),
    CONSTRAINT chk_pots_contribution_cap CHECK (contribution_cap > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE pot_awards (
    id BIGINT NOT NULL AUTO_INCREMENT,
    pot_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    amount_awarded BIGINT NOT NULL,
    odd_chip_amount BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_pot_awards PRIMARY KEY (id),
    CONSTRAINT uk_pot_awards_pot_user UNIQUE (pot_id, user_id),
    CONSTRAINT fk_pot_awards_pot FOREIGN KEY (pot_id) REFERENCES pots (id) ON DELETE CASCADE,
    CONSTRAINT fk_pot_awards_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_pot_awards_amount CHECK (amount_awarded > 0),
    CONSTRAINT chk_pot_awards_odd_chip CHECK (odd_chip_amount >= 0 AND odd_chip_amount <= amount_awarded),
    INDEX idx_pot_awards_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE uncalled_bet_returns (
    id BIGINT NOT NULL AUTO_INCREMENT,
    poker_hand_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    amount BIGINT NOT NULL,
    CONSTRAINT pk_uncalled_bet_returns PRIMARY KEY (id),
    CONSTRAINT uk_uncalled_returns_hand_user UNIQUE (poker_hand_id, user_id),
    CONSTRAINT fk_uncalled_returns_hand FOREIGN KEY (poker_hand_id) REFERENCES poker_hands (id) ON DELETE CASCADE,
    CONSTRAINT fk_uncalled_returns_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_uncalled_returns_amount CHECK (amount > 0),
    INDEX idx_uncalled_returns_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
