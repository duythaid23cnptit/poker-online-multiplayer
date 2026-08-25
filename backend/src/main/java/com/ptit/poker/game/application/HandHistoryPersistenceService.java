package com.ptit.poker.game.application;

import com.ptit.poker.game.domain.state.GamePhase;
import com.ptit.poker.game.domain.state.GameState;
import com.ptit.poker.game.domain.state.PokerPlayer;
import com.ptit.poker.game.domain.settlement.HandSettlementResult;
import com.ptit.poker.game.domain.pot.Pot;
import com.ptit.poker.game.domain.settlement.PotAward;
import com.ptit.poker.game.domain.pot.UncalledBetReturn;
import com.ptit.poker.game.infrastructure.persistence.GameSessionEntity;
import com.ptit.poker.game.infrastructure.persistence.GameSessionRepository;
import com.ptit.poker.game.infrastructure.persistence.HandEndReason;
import com.ptit.poker.game.infrastructure.persistence.HandPlayerEntity;
import com.ptit.poker.game.infrastructure.persistence.HandPlayerRepository;
import com.ptit.poker.game.infrastructure.persistence.PlayerActionEntity;
import com.ptit.poker.game.infrastructure.persistence.PlayerActionRepository;
import com.ptit.poker.game.infrastructure.persistence.PokerHandEntity;
import com.ptit.poker.game.infrastructure.persistence.PokerHandRepository;
import com.ptit.poker.game.infrastructure.persistence.PotAwardEntity;
import com.ptit.poker.game.infrastructure.persistence.PotAwardRepository;
import com.ptit.poker.game.infrastructure.persistence.PotEntity;
import com.ptit.poker.game.infrastructure.persistence.PotRepository;
import com.ptit.poker.game.infrastructure.persistence.UncalledBetReturnEntity;
import com.ptit.poker.game.infrastructure.persistence.UncalledBetReturnRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!bootstrap")
public class HandHistoryPersistenceService {
    private final GameSessionRepository sessions;
    private final PokerHandRepository hands;
    private final HandPlayerRepository handPlayers;
    private final PlayerActionRepository actions;
    private final PotRepository pots;
    private final PotAwardRepository awards;
    private final UncalledBetReturnRepository returns;
    private final Clock clock;

    public HandHistoryPersistenceService(GameSessionRepository sessions,
                                         PokerHandRepository hands,
                                         HandPlayerRepository handPlayers,
                                         PlayerActionRepository actions,
                                         PotRepository pots,
                                         PotAwardRepository awards,
                                         UncalledBetReturnRepository returns,
                                         Clock clock) {
        this.sessions = sessions;
        this.hands = hands;
        this.handPlayers = handPlayers;
        this.actions = actions;
        this.pots = pots;
        this.awards = awards;
        this.returns = returns;
        this.clock = clock;
    }

    @Transactional
    public HandHistoryHandle startHand(long gameSessionId,
                                       long handNumber,
                                       long smallBlindAmount,
                                       long bigBlindAmount,
                                       GameState state) {
        requireActiveSession(gameSessionId);
        if (state.phase() != GamePhase.PRE_FLOP || state.isFinanciallySettled()) {
            throw failure("HAND_START_STATE_INVALID");
        }

        PokerHandEntity hand = hands.saveAndFlush(new PokerHandEntity(
                gameSessionId, handNumber, state.dealerPosition(), state.smallBlindPosition(),
                state.bigBlindPosition(), smallBlindAmount, bigBlindAmount, clock.instant(),
                null, GamePhase.PRE_FLOP, "", null));

        Map<Long, Long> startingChips = new HashMap<>();
        for (PokerPlayer player : state.players()) {
            startingChips.put(player.userId(), Math.addExact(player.tableChips(), player.totalCommitted()));
        }
        return new HandHistoryHandle(hand.getId(), startingChips);
    }

    @Transactional
    public AcceptedActionRecord recordAcceptedAction(long pokerHandId, AcceptedActionHistory accepted) {
        PokerHandEntity hand = requireOpenHand(pokerHandId);
        if (accepted.phase() == GamePhase.SHOWDOWN || accepted.phase() == GamePhase.FINISHED) {
            throw failure("ACTION_PHASE_INVALID");
        }

        long next = actions.countByPokerHandId(hand.getId()) + 1;
        if (next > Integer.MAX_VALUE) {
            throw failure("ACTION_SEQUENCE_EXHAUSTED");
        }
        PlayerActionEntity saved = actions.saveAndFlush(new PlayerActionEntity(
                hand.getId(), accepted.userId(), next, accepted.phase(), accepted.actionType(),
                accepted.amountCommittedByAction(), accepted.resultingPlayerCurrentBet(),
                accepted.resultingGameCurrentBet(), accepted.resultingTableChips(),
                accepted.turnId(), accepted.clientActionId(), clock.instant()));
        return new AcceptedActionRecord(saved.getId(), saved.getActionSequence());
    }

    @Transactional
    public CompletedHandRecord completeHand(HandHistoryHandle handle,
                                             GameState state,
                                             HandSettlementResult settlement,
                                             HandCompletionReason reason) {
        PokerHandEntity hand = requireOpenHand(handle.pokerHandId());
        validateCompletion(state, settlement, reason);
        validatePlayerSnapshots(handle, state, settlement);

        hand.complete(clock.instant(), GamePhase.FINISHED, CanonicalCardCodec.serialize(state.communityCards()),
                HandEndReason.valueOf(reason.name()));

        List<HandPlayerEntity> playerRows = new ArrayList<>();
        for (PokerPlayer player : state.players()) {
            Long starting = handle.startingTableChipsByUser().get(player.userId());
            Long committed = settlement.totalCommittedByUser().get(player.userId());
            playerRows.add(new HandPlayerEntity(hand.getId(), player.userId(), player.seatNumber(), starting,
                    player.tableChips(), committed, player.playerState(), player.isConnected(),
                    player.isLeaving(), CanonicalCardCodec.serialize(player.holeCards())));
        }
        handPlayers.saveAll(playerRows);

        Map<Integer, PotAward> awardsByIndex = indexAwards(settlement);
        int awardCount = 0;
        for (Pot pot : settlement.contestedPots()) {
            PotAward award = awardsByIndex.remove(pot.index());
            if (award == null) {
                throw failure("POT_AWARD_MISSING");
            }
            PotEntity potRow = pots.saveAndFlush(new PotEntity(hand.getId(), pot.index(), pot.type(),
                    pot.amount(), pot.contributionCap()));
            for (Map.Entry<Long, Long> payout : award.winnerPayouts().entrySet()) {
                long oddChipAmount = award.oddChipUserIds().contains(payout.getKey()) ? 1 : 0;
                awards.save(new PotAwardEntity(potRow.getId(), payout.getKey(), payout.getValue(), oddChipAmount));
                awardCount++;
            }
        }
        if (!awardsByIndex.isEmpty()) {
            throw failure("POT_AWARD_WITHOUT_POT");
        }

        for (UncalledBetReturn uncalled : settlement.uncalledBetReturns()) {
            returns.save(new UncalledBetReturnEntity(hand.getId(), uncalled.userId(), uncalled.amount()));
        }

        hands.flush();
        handPlayers.flush();
        awards.flush();
        returns.flush();
        return new CompletedHandRecord(hand.getId(), playerRows.size(), settlement.contestedPots().size(),
                awardCount, settlement.uncalledBetReturns().size());
    }

    private GameSessionEntity requireActiveSession(long id) {
        GameSessionEntity session = sessions.findByIdForUpdate(id)
                .orElseThrow(() -> failure("GAME_SESSION_NOT_FOUND"));
        if (session.getStatus() != com.ptit.poker.game.infrastructure.persistence.GameSessionStatus.ACTIVE) {
            throw failure("GAME_SESSION_NOT_ACTIVE");
        }
        return session;
    }

    private PokerHandEntity requireOpenHand(long id) {
        PokerHandEntity hand = hands.findByIdForUpdate(id)
                .orElseThrow(() -> failure("POKER_HAND_NOT_FOUND"));
        if (hand.getEndReason() != null) {
            throw failure("POKER_HAND_ALREADY_COMPLETED");
        }
        return hand;
    }

    private static void validateCompletion(GameState state,
                                           HandSettlementResult settlement,
                                           HandCompletionReason reason) {
        if (state.phase() != GamePhase.FINISHED || !state.isFinanciallySettled()) {
            throw failure("HAND_NOT_SETTLED");
        }
        if (settlement.foldOnly() != (reason == HandCompletionReason.ALL_OTHERS_FOLDED)) {
            throw failure("HAND_COMPLETION_REASON_MISMATCH");
        }
    }

    private static void validatePlayerSnapshots(HandHistoryHandle handle,
                                                GameState state,
                                                HandSettlementResult settlement) {
        Set<Long> participantIds = new LinkedHashSet<>();
        for (PokerPlayer player : state.players()) {
            participantIds.add(player.userId());
            boolean startMissing = !handle.startingTableChipsByUser().containsKey(player.userId());
            boolean commitmentMissing = !settlement.totalCommittedByUser().containsKey(player.userId());
            if (startMissing || commitmentMissing) {
                throw failure("HAND_PLAYER_SNAPSHOT_MISSING:userId=" + player.userId()
                        + ",seat=" + player.seatNumber()
                        + ",starting=" + (startMissing ? "missing" : "present")
                        + ",committed=" + (commitmentMissing ? "missing" : "present"));
            }
        }
        if (!handle.startingTableChipsByUser().keySet().equals(participantIds)
                || !settlement.totalCommittedByUser().keySet().equals(participantIds)) {
            throw failure("HAND_PLAYER_SNAPSHOT_PARTICIPANTS_MISMATCH");
        }
    }

    private static Map<Integer, PotAward> indexAwards(HandSettlementResult settlement) {
        Map<Integer, PotAward> result = new HashMap<>();
        for (PotAward award : settlement.potAwards()) {
            if (result.put(award.potIndex(), award) != null) {
                throw failure("DUPLICATE_POT_AWARD");
            }
        }
        return result;
    }

    private static GameplayHistoryException failure(String code) {
        return new GameplayHistoryException(code, code);
    }
}
