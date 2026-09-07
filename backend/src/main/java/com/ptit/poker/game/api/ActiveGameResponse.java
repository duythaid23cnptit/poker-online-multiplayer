package com.ptit.poker.game.api;

import java.util.UUID;

public record ActiveGameResponse(long roomId, UUID gameId, long gameSessionId, long handId, long handNumber) {}
