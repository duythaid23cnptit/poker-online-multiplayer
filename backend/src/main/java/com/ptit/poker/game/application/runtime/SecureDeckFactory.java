package com.ptit.poker.game.application.runtime;

import com.ptit.poker.game.domain.card.Deck;
import org.springframework.stereotype.Component;

@Component
public class SecureDeckFactory implements DeckFactory {
    @Override public Deck create() { return new Deck(); }
}
