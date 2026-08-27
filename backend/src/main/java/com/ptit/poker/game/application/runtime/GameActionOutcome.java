package com.ptit.poker.game.application.runtime;

import com.ptit.poker.game.domain.betting.PokerActionType;
import com.ptit.poker.game.domain.settlement.HandSettlementResult;
import java.util.UUID;

public record GameActionOutcome(GameRuntimeView before, GameRuntimeView after, long userId, int seat,
                                PokerActionType actionType, long amountCommitted, long resultingCurrentBet,
                                long resultingTableChips, UUID clientActionId,
                                HandSettlementResult settlement, boolean automatic) {}
