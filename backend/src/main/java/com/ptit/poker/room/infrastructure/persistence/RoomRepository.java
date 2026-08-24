package com.ptit.poker.room.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<RoomEntity, Long> {
    List<RoomEntity> findAllByStatusOrderByLastActivityAtDesc(com.ptit.poker.room.domain.RoomStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select room from RoomEntity room where room.id = :id")
    Optional<RoomEntity> findByIdForUpdate(Long id);
}
