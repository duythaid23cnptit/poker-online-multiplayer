package com.ptit.poker.game.domain.state;

/** Streets and terminal phases of one active Texas Hold'em hand. */
public enum GamePhase {
    PRE_FLOP,
    FLOP,
    TURN,
    RIVER,
    SHOWDOWN,
    FINISHED
}
