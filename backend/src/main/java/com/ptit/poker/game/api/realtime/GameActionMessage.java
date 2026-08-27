package com.ptit.poker.game.api.realtime;

import com.ptit.poker.game.application.runtime.GameActionIntent;
import com.ptit.poker.game.domain.betting.PokerActionType;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record GameActionMessage(@NotNull PokerActionType actionType, Long amount,
                                @NotNull UUID turnId, @NotNull UUID clientActionId) {
    public GameActionIntent toIntent() {
        long target = amount == null ? 0 : amount;
        if ((actionType == PokerActionType.BET || actionType == PokerActionType.RAISE) && amount == null)
            throw new IllegalArgumentException("amount is required for BET and RAISE");
        if (target < 0) throw new IllegalArgumentException("amount must not be negative");
        return new GameActionIntent(turnId, clientActionId, actionType, target);
    }
}
