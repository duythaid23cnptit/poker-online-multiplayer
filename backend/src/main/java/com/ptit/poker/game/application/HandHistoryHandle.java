package com.ptit.poker.game.application;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record HandHistoryHandle(long pokerHandId, Map<Long, Long> startingTableChipsByUser) {
    public HandHistoryHandle {
        if (pokerHandId <= 0) throw new IllegalArgumentException("pokerHandId must be positive");
        startingTableChipsByUser = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(
                startingTableChipsByUser, "startingTableChipsByUser must not be null")));
    }
}
