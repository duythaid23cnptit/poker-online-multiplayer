package com.ptit.poker.game.application;

import com.ptit.poker.game.infrastructure.persistence.GameSessionStatus;
import java.time.Instant;

public record GameSessionView(long id, long roomId, GameSessionStatus status, Instant startedAt, Instant endedAt) {}
