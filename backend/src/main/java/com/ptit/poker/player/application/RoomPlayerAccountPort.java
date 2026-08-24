package com.ptit.poker.player.application;

public interface RoomPlayerAccountPort {
    void requireActive(Long userId);
    String username(Long userId);
    void debit(Long userId, long amount);
    void credit(Long userId, long amount);
}
