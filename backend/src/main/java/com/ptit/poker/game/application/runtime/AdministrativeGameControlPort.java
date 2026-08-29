package com.ptit.poker.game.application.runtime;

public interface AdministrativeGameControlPort {
    boolean hasPendingTerminationForRoom(long roomId);
}
