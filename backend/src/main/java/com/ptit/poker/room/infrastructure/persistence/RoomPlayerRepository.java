package com.ptit.poker.room.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

import java.util.List;

import java.util.Optional;

public interface RoomPlayerRepository extends JpaRepository<RoomPlayerEntity, Long> {

    Optional<RoomPlayerEntity> findByRoomIdAndUserId(Long roomId, Long userId);

    List<RoomPlayerEntity> findAllByRoomIdAndLeftAtIsNullOrderById(Long roomId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select member from RoomPlayerEntity member where member.roomId = :roomId and member.leftAt is null order by member.id")
    List<RoomPlayerEntity> findAllActiveByRoomIdForUpdate(Long roomId);

    long countByRoomIdAndSeatNumberIsNotNullAndLeftAtIsNull(Long roomId);

    boolean existsByRoomIdAndSeatNumberAndLeftAtIsNull(Long roomId, Integer seatNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select member from RoomPlayerEntity member where member.roomId = :roomId and member.userId = :userId")
    Optional<RoomPlayerEntity> findByRoomIdAndUserIdForUpdate(Long roomId, Long userId);
}
