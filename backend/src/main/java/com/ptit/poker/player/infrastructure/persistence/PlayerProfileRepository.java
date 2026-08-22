package com.ptit.poker.player.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlayerProfileRepository extends JpaRepository<PlayerProfileEntity, Long> {

    Optional<PlayerProfileEntity> findByUserId(Long userId);
}
