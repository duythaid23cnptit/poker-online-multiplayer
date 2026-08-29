package com.ptit.poker.social.application;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface SocialPlayerQueryPort {
    Optional<SafePlayerSummary> findActivePlayer(long userId);
    Map<Long, SafePlayerSummary> findSafePlayerSummaries(Collection<Long> userIds);

    record SafePlayerSummary(long userId, String displayName, String avatarUrl) {}
}
