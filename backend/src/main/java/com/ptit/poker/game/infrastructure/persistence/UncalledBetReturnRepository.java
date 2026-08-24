package com.ptit.poker.game.infrastructure.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface UncalledBetReturnRepository extends JpaRepository<UncalledBetReturnEntity, Long> {
    List<UncalledBetReturnEntity> findAllByPokerHandIdOrderById(Long pokerHandId);
}
