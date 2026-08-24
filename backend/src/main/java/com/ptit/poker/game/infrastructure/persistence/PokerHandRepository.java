package com.ptit.poker.game.infrastructure.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface PokerHandRepository extends JpaRepository<PokerHandEntity, Long> {
    List<PokerHandEntity> findAllByGameSessionIdOrderByHandNumber(Long gameSessionId);
}
