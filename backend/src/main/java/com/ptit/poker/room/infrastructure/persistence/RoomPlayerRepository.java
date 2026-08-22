package com.ptit.poker.room.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoomPlayerRepository extends JpaRepository<RoomPlayerEntity, Long> {

    Optional<RoomPlayerEntity> findByRoomIdAndUserId(Long roomId, Long userId);
}

