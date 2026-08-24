package com.ptit.poker.game.infrastructure.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface PlayerActionRepository extends JpaRepository<PlayerActionEntity, Long> {
    List<PlayerActionEntity> findAllByPokerHandIdOrderByActionSequence(Long pokerHandId);
}
