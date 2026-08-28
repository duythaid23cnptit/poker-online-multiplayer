package com.ptit.poker.analytics.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PlayerStatisticsRepository extends JpaRepository<PlayerStatisticsEntity,Long> {
    @Modifying @Query(value="""
        INSERT INTO player_statistics(user_id,total_games,total_hands,total_wins,total_losses,win_rate,
          total_chips_won,total_chips_lost,net_chip,largest_pot_won,average_playing_seconds,updated_at)
        VALUES (:userId,:games,:hands,:wins,:losses,:rate,:won,:lost,:net,:largest,:average,:updated)
        ON DUPLICATE KEY UPDATE total_games=VALUES(total_games),total_hands=VALUES(total_hands),
          total_wins=VALUES(total_wins),total_losses=VALUES(total_losses),win_rate=VALUES(win_rate),
          total_chips_won=VALUES(total_chips_won),total_chips_lost=VALUES(total_chips_lost),net_chip=VALUES(net_chip),
          largest_pot_won=VALUES(largest_pot_won),average_playing_seconds=VALUES(average_playing_seconds),updated_at=VALUES(updated_at)
        """,nativeQuery=true)
    void replace(@Param("userId")long userId,@Param("games")long games,@Param("hands")long hands,
                 @Param("wins")long wins,@Param("losses")long losses,@Param("rate")BigDecimal rate,
                 @Param("won")long won,@Param("lost")long lost,@Param("net")long net,
                 @Param("largest")long largest,@Param("average")long average,@Param("updated")Instant updated);
}
