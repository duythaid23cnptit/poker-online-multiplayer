package com.ptit.poker.game.infrastructure.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface PotRepository extends JpaRepository<PotEntity, Long> {
    List<PotEntity> findAllByPokerHandIdOrderByPotIndex(Long pokerHandId);
}
