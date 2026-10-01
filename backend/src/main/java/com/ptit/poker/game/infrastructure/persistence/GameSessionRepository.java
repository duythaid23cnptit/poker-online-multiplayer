package com.ptit.poker.game.infrastructure.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface GameSessionRepository extends JpaRepository<GameSessionEntity, Long> {
    List<GameSessionEntity> findAllByRoomIdOrderByStartedAtDesc(Long roomId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from GameSessionEntity session where session.id = :id")
    Optional<GameSessionEntity> findByIdForUpdate(Long id);
    Optional<GameSessionEntity> findFirstByRoomIdAndStatusOrderByStartedAtDesc(Long roomId, GameSessionStatus status);
    Optional<GameSessionEntity> findByGameId(String gameId);
    @Query("select session.id from GameSessionEntity session where session.status = :status order by session.id")
    List<Long> findIdsByStatus(@Param("status") GameSessionStatus status);
}
