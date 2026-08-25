package com.ptit.poker.game.application.runtime;

import com.ptit.poker.game.domain.card.Deck;

@FunctionalInterface
public interface DeckFactory { Deck create(); }
