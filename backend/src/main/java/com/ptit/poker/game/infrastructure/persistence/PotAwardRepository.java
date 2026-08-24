package com.ptit.poker.game.infrastructure.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface PotAwardRepository extends JpaRepository<PotAwardEntity, Long> {
    List<PotAwardEntity> findAllByPotIdOrderById(Long potId);
}
