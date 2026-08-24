package com.ptit.poker.game.infrastructure.persistence;

import com.ptit.poker.game.domain.betting.PokerActionType;
import com.ptit.poker.game.domain.state.GamePhase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "player_actions")
public class PlayerActionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "poker_hand_id", nullable = false) private Long pokerHandId;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "action_sequence", nullable = false) private long actionSequence;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private GamePhase phase;
    @Enumerated(EnumType.STRING) @Column(name = "action_type", nullable = false, length = 20) private PokerActionType actionType;
    @Column(name = "amount_committed_by_action", nullable = false) private long amountCommittedByAction;
    @Column(name = "resulting_player_current_bet", nullable = false) private long resultingPlayerCurrentBet;
    @Column(name = "resulting_game_current_bet", nullable = false) private long resultingGameCurrentBet;
    @Column(name = "resulting_table_chips", nullable = false) private long resultingTableChips;
    @Column(name = "turn_id", length = 36) private String turnId;
    @Column(name = "client_action_id", length = 36) private String clientActionId;
    @Column(name = "acted_at", nullable = false) private Instant actedAt;
    protected PlayerActionEntity() {}
    public PlayerActionEntity(Long pokerHandId, Long userId, long actionSequence, GamePhase phase,
                              PokerActionType actionType, long amountCommittedByAction,
                              long resultingPlayerCurrentBet, long resultingGameCurrentBet,
                              long resultingTableChips, UUID turnId, UUID clientActionId, Instant actedAt) {
        this.pokerHandId = pokerHandId; this.userId = userId; this.actionSequence = actionSequence;
        this.phase = phase; this.actionType = actionType; this.amountCommittedByAction = amountCommittedByAction;
        this.resultingPlayerCurrentBet = resultingPlayerCurrentBet;
        this.resultingGameCurrentBet = resultingGameCurrentBet; this.resultingTableChips = resultingTableChips;
        this.turnId = turnId == null ? null : turnId.toString();
        this.clientActionId = clientActionId == null ? null : clientActionId.toString();
        this.actedAt = actedAt;
    }
    public Long getId() { return id; }
    public long getActionSequence() { return actionSequence; }
    public GamePhase getPhase() { return phase; }
    public PokerActionType getActionType() { return actionType; }
}
