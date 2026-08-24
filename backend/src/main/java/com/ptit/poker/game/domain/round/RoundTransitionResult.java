package com.ptit.poker.game.domain.round;

import com.ptit.poker.game.domain.betting.BettingResult;
import com.ptit.poker.game.domain.card.Card;
import com.ptit.poker.game.domain.state.GamePhase;

import java.util.List;
import java.util.UUID;

/** Immutable result of one coordinated action, turn update, and optional street transition. */
public record RoundTransitionResult(
        BettingResult bettingResult,
        GamePhase resultingPhase,
        Long previousActorUserId,
        Long currentActorUserId,
        boolean bettingRoundCompleted,
        boolean streetAdvanced,
        boolean handDecidedByFold,
        List<Card> communityCardsDealt,
        long stateVersion,
        UUID turnId) {

    public RoundTransitionResult {
        communityCardsDealt = List.copyOf(communityCardsDealt);
    }
}
