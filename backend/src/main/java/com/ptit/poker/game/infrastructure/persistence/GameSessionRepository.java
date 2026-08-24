package com.ptit.poker.game.infrastructure.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface GameSessionRepository extends JpaRepository<GameSessionEntity, Long> {
    List<GameSessionEntity> findAllByRoomIdOrderByStartedAtDesc(Long roomId);
}
