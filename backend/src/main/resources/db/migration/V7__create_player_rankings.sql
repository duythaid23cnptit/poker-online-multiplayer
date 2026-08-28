CREATE TABLE player_rankings (
 user_id BIGINT NOT NULL, rating INT NOT NULL DEFAULT 1000, games_rated BIGINT NOT NULL DEFAULT 0,
 peak_rating INT NOT NULL DEFAULT 1000, updated_at DATETIME(6) NOT NULL,
 CONSTRAINT pk_player_rankings PRIMARY KEY(user_id),
 CONSTRAINT fk_player_rankings_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,
 CONSTRAINT chk_player_rankings_rating CHECK(rating >= 0),
 CONSTRAINT chk_player_rankings_games CHECK(games_rated >= 0),
 CONSTRAINT chk_player_rankings_peak CHECK(peak_rating >= 0),
 INDEX idx_player_rankings_board(rating DESC,games_rated DESC,user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ranking_history (
 id BIGINT NOT NULL AUTO_INCREMENT, user_id BIGINT NOT NULL, game_session_id BIGINT NOT NULL,
 old_rating INT NOT NULL, new_rating INT NOT NULL, rating_delta INT NOT NULL, session_net BIGINT NOT NULL,
 placement INT NOT NULL, participant_count INT NOT NULL, created_at DATETIME(6) NOT NULL,
 CONSTRAINT pk_ranking_history PRIMARY KEY(id),
 CONSTRAINT uk_ranking_history_user_session UNIQUE(user_id,game_session_id),
 CONSTRAINT fk_ranking_history_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,
 CONSTRAINT fk_ranking_history_session FOREIGN KEY(game_session_id) REFERENCES game_sessions(id) ON DELETE CASCADE,
 CONSTRAINT chk_ranking_history_ratings CHECK(old_rating >= 0 AND new_rating >= 0),
 CONSTRAINT chk_ranking_history_placement CHECK(placement > 0 AND participant_count >= 2 AND placement <= participant_count),
 INDEX idx_ranking_history_user_created(user_id,created_at), INDEX idx_ranking_history_session(game_session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
