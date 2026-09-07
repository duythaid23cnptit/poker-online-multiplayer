package com.ptit.poker.game.infrastructure.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
public interface PokerHandRepository extends JpaRepository<PokerHandEntity, Long> {
    List<PokerHandEntity> findAllByGameSessionIdOrderByHandNumber(Long gameSessionId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select hand from PokerHandEntity hand where hand.id = :id")
    Optional<PokerHandEntity> findByIdForUpdate(Long id);
    Optional<PokerHandEntity> findFirstByGameSessionIdAndEndReasonIsNotNullOrderByHandNumberDesc(Long gameSessionId);
}
