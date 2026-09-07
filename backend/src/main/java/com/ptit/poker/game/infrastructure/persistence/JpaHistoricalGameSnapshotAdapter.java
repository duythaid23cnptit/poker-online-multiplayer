package com.ptit.poker.game.infrastructure.persistence;

import com.ptit.poker.game.application.CanonicalCardCodec;
import com.ptit.poker.game.application.HistoricalGameSnapshot;
import com.ptit.poker.game.application.HistoricalGameSnapshotPort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("!bootstrap")
public class JpaHistoricalGameSnapshotAdapter implements HistoricalGameSnapshotPort {
    private final GameSessionRepository sessions;
    private final PokerHandRepository hands;
    private final HandPlayerRepository players;

    public JpaHistoricalGameSnapshotAdapter(GameSessionRepository sessions, PokerHandRepository hands,
                                            HandPlayerRepository players) {
        this.sessions = sessions;
        this.hands = hands;
        this.players = players;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<HistoricalGameSnapshot> findFinishedForParticipant(UUID gameId, long userId) {
        GameSessionEntity session = finishedSession(gameId).orElse(null);
        if (session == null || players.countByGameSessionIdAndUserId(session.getId(), userId) == 0) return Optional.empty();
        PokerHandEntity hand = hands.findFirstByGameSessionIdAndEndReasonIsNotNullOrderByHandNumberDesc(session.getId())
                .orElse(null);
        if (hand == null) return Optional.empty();
        List<HandPlayerEntity> finalPlayers = players.findAllByPokerHandIdOrderBySeatNumber(hand.getId());
        List<com.ptit.poker.game.domain.card.Card> ownCards = finalPlayers.stream()
                .filter(player -> player.getUserId() == userId)
                .findFirst().map(HandPlayerEntity::getHoleCards).map(CanonicalCardCodec::deserialize).orElse(List.of());
        return Optional.of(new HistoricalGameSnapshot(session.getRoomId(), gameId, session.getId(), hand.getId(),
                hand.getHandNumber(), hand.getDealerSeat(), hand.getSmallBlindSeat(), hand.getBigBlindSeat(),
                hand.getFinalPhase(), CanonicalCardCodec.deserialize(hand.getBoardCards()), finalPlayers.stream()
                .map(player -> new HistoricalGameSnapshot.Player(player.getUserId(), player.getSeatNumber(),
                        player.getEndingTableChips(), player.getParticipationState(), player.isConnectedAtEnd(),
                        player.isLeavingAtEnd())).toList(), ownCards));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isFinishedParticipant(UUID gameId, long userId) {
        return finishedSession(gameId)
                .map(session -> players.countByGameSessionIdAndUserId(session.getId(), userId) > 0).orElse(false);
    }

    private Optional<GameSessionEntity> finishedSession(UUID gameId) {
        return sessions.findByGameId(gameId.toString())
                .filter(session -> session.getStatus() == GameSessionStatus.FINISHED);
    }
}
