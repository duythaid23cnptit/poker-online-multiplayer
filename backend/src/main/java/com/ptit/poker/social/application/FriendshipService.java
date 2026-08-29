package com.ptit.poker.social.application;

import com.ptit.poker.social.domain.FriendshipStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
@Profile("!bootstrap")
public class FriendshipService {
    private final FriendshipPersistencePort friendships;
    private final SocialPlayerQueryPort players;
    private final Clock clock;

    public FriendshipService(FriendshipPersistencePort friendships, SocialPlayerQueryPort players, Clock clock) {
        this.friendships = friendships;
        this.players = players;
        this.clock = clock;
    }

    @Transactional
    public FriendshipSendResult sendFriendRequest(long requesterUserId, long recipientUserId) {
        if (requesterUserId == recipientUserId) {
            throw FriendshipException.conflict("SELF_FRIEND_REQUEST", "A user cannot send a friend request to themselves");
        }
        SocialPlayerQueryPort.SafePlayerSummary recipient = players.findActivePlayer(recipientUserId)
                .orElseThrow(() -> FriendshipException.notFound("PLAYER_NOT_FOUND", "Player was not found"));
        Instant now = clock.instant();
        if (friendships.insertPending(requesterUserId, recipientUserId, now)) {
            FriendshipRecord inserted = friendships.findCanonicalPairForUpdate(requesterUserId, recipientUserId)
                    .orElseThrow(FriendshipException::internalPersistence);
            return new FriendshipSendResult(toView(inserted, requesterUserId, Map.of(recipient.userId(), recipient)), true);
        }

        FriendshipRecord existing = friendships.findCanonicalPairForUpdate(requesterUserId, recipientUserId)
                .orElseThrow(FriendshipException::internalPersistence);
        FriendshipRecord result = switch (existing.status()) {
            case ACCEPTED -> throw FriendshipException.conflict(
                    "FRIENDSHIP_ALREADY_EXISTS", "The players are already friends");
            case REJECTED -> friendships.reopen(existing.id(), requesterUserId, recipientUserId, now);
            case PENDING -> {
                if (existing.requesterUserId() == requesterUserId) {
                    throw FriendshipException.conflict(
                            "FRIEND_REQUEST_ALREADY_EXISTS", "A pending friend request already exists");
                }
                yield friendships.transition(existing.id(), FriendshipStatus.ACCEPTED, now);
            }
        };
        return new FriendshipSendResult(toView(result, requesterUserId, Map.of(recipient.userId(), recipient)), false);
    }

    @Transactional(readOnly = true)
    public List<FriendshipView> listFriendRequests(long userId, FriendRequestDirection direction) {
        List<FriendshipRecord> records = direction == FriendRequestDirection.INCOMING
                ? friendships.listIncomingPending(userId)
                : friendships.listOutgoingPending(userId);
        return toViews(records, userId, false);
    }

    @Transactional
    public FriendshipView acceptFriendRequest(long userId, long requestId) {
        return respond(userId, requestId, FriendshipStatus.ACCEPTED);
    }

    @Transactional
    public FriendshipView rejectFriendRequest(long userId, long requestId) {
        return respond(userId, requestId, FriendshipStatus.REJECTED);
    }

    @Transactional(readOnly = true)
    public List<FriendshipView> listFriends(long userId) {
        return toViews(friendships.listAccepted(userId), userId, true);
    }

    @Transactional
    public void removeFriend(long userId, long otherUserId) {
        if (!friendships.deleteAcceptedPair(userId, otherUserId)) {
            throw FriendshipException.notFound("FRIENDSHIP_NOT_FOUND", "Friendship was not found");
        }
    }

    private FriendshipView respond(long userId, long requestId, FriendshipStatus response) {
        FriendshipRecord request = friendships.findRequestByIdForUpdate(requestId)
                .orElseThrow(() -> FriendshipException.notFound("FRIEND_REQUEST_NOT_FOUND", "Friend request was not found"));
        if (request.recipientUserId() != userId) {
            throw FriendshipException.forbidden(
                    "FRIEND_REQUEST_NOT_AUTHORIZED", "Only the request recipient may respond");
        }
        if (request.status() != FriendshipStatus.PENDING) {
            throw FriendshipException.conflict("FRIEND_REQUEST_NOT_PENDING", "Friend request is no longer pending");
        }
        FriendshipRecord updated = friendships.transition(request.id(), response, clock.instant());
        return toViews(List.of(updated), userId, false).getFirst();
    }

    private List<FriendshipView> toViews(List<FriendshipRecord> records, long userId, boolean sortFriends) {
        LinkedHashSet<Long> otherIds = new LinkedHashSet<>();
        records.forEach(record -> otherIds.add(record.otherUserId(userId)));
        Map<Long, SocialPlayerQueryPort.SafePlayerSummary> summaries = players.findSafePlayerSummaries(otherIds);
        List<FriendshipView> views = records.stream().map(record -> toView(record, userId, summaries)).toList();
        if (!sortFriends) return views;
        return views.stream().sorted(Comparator
                .comparing((FriendshipView view) -> view.otherPlayer().displayName(), String.CASE_INSENSITIVE_ORDER)
                .thenComparingLong(view -> view.otherPlayer().userId())).toList();
    }

    private FriendshipView toView(FriendshipRecord record, long userId,
                                  Map<Long, SocialPlayerQueryPort.SafePlayerSummary> summaries) {
        long otherId = record.otherUserId(userId);
        SocialPlayerQueryPort.SafePlayerSummary summary = summaries.get(otherId);
        if (summary == null) throw FriendshipException.internalPersistence();
        return new FriendshipView(record.id(), record.status(), record.createdAt(), record.respondedAt(), summary);
    }
}
