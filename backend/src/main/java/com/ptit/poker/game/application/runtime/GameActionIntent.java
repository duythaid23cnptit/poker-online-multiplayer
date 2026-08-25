package com.ptit.poker.game.application.runtime;

import com.ptit.poker.game.domain.betting.PokerActionType;
import java.util.UUID;

public record GameActionIntent(UUID turnId, UUID clientActionId, PokerActionType type, long targetCurrentBet) {}
