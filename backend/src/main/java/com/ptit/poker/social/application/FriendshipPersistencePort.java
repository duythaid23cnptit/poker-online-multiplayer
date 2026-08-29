package com.ptit.poker.social.application;

import com.ptit.poker.social.domain.FriendshipStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FriendshipPersistencePort {
    boolean insertPending(long requesterUserId, long recipientUserId, Instant createdAt);
    Optional<FriendshipRecord> findCanonicalPairForUpdate(long userA, long userB);
    Optional<FriendshipRecord> findRequestByIdForUpdate(long requestId);
    FriendshipRecord transition(long requestId, FriendshipStatus status, Instant respondedAt);
    FriendshipRecord reopen(long requestId, long requesterUserId, long recipientUserId, Instant createdAt);
    boolean deleteAcceptedPair(long userA, long userB);
    List<FriendshipRecord> listIncomingPending(long recipientUserId);
    List<FriendshipRecord> listOutgoingPending(long requesterUserId);
    List<FriendshipRecord> listAccepted(long userId);
}
