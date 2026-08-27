package com.ptit.poker.game.api.realtime;

import static org.assertj.core.api.Assertions.*;
import com.ptit.poker.game.domain.betting.PokerActionType;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

class GameActionMessageTests {
    @ParameterizedTest @MethodSource("actions")
    void mapsOnlyIntentFields(PokerActionType type, Long amount, long target) {
        UUID turn = UUID.randomUUID(), client = UUID.randomUUID();
        var intent = new GameActionMessage(type, amount, turn, client).toIntent();
        assertThat(intent.type()).isEqualTo(type);
        assertThat(intent.targetCurrentBet()).isEqualTo(target);
        assertThat(intent.turnId()).isEqualTo(turn);
        assertThat(intent.clientActionId()).isEqualTo(client);
    }
    static Stream<Arguments> actions() { return Stream.of(
            Arguments.of(PokerActionType.FOLD, null, 0L), Arguments.of(PokerActionType.CHECK, null, 0L),
            Arguments.of(PokerActionType.CALL, null, 0L), Arguments.of(PokerActionType.BET, 250L, 250L),
            Arguments.of(PokerActionType.RAISE, 400L, 400L), Arguments.of(PokerActionType.ALL_IN, null, 0L)); }
    @Test void hasNoSpoofableUserField() {
        assertThat(GameActionMessage.class.getRecordComponents()).extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("actionType", "amount", "turnId", "clientActionId");
    }
    @Test void betAndRaiseRequireAmount() {
        assertThatThrownBy(() -> new GameActionMessage(PokerActionType.BET, null, UUID.randomUUID(), UUID.randomUUID()).toIntent())
                .isInstanceOf(IllegalArgumentException.class);
    }
}
