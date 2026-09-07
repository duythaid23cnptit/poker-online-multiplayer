package com.ptit.poker.game.infrastructure.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
public interface HandPlayerRepository extends JpaRepository<HandPlayerEntity, Long> {
    List<HandPlayerEntity> findAllByPokerHandIdOrderBySeatNumber(Long pokerHandId);
    @Query("select count(player) from HandPlayerEntity player where player.userId = :userId "
            + "and player.pokerHandId in (select hand.id from PokerHandEntity hand where hand.gameSessionId = :sessionId)")
    long countByGameSessionIdAndUserId(Long sessionId, Long userId);
}
