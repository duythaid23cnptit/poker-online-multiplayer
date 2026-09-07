package com.ptit.poker.game.api;

import java.util.UUID;

public record GameDepartureResponse(long roomId, UUID gameId, boolean changed, boolean deferred) {}
