package com.ptit.poker.game.infrastructure.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface HandPlayerRepository extends JpaRepository<HandPlayerEntity, Long> {
    List<HandPlayerEntity> findAllByPokerHandIdOrderBySeatNumber(Long pokerHandId);
}
