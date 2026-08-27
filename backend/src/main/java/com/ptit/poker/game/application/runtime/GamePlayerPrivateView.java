package com.ptit.poker.game.application.runtime;

import com.ptit.poker.game.domain.betting.LegalActions;
import com.ptit.poker.game.domain.card.Card;
import java.util.List;
import java.util.UUID;

public record GamePlayerPrivateView(long userId, long handId, UUID gameId, UUID turnId, long stateVersion,
                                    List<Card> holeCards, LegalActions legalActions, long tableChips) {
    public GamePlayerPrivateView { holeCards = List.copyOf(holeCards); }
}
