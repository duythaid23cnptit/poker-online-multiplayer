package com.ptit.poker.game.domain.pot;

import com.ptit.poker.game.domain.state.PokerPlayer;
import com.ptit.poker.game.domain.state.PokerPlayerState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PotBuilderTests {

    @Test
    void equalContributionsCreateOnlyOneMainPot() {
        PotConstructionResult result = calculate(100, 100, 100);

        Pot main = result.mainPot().orElseThrow();
        assertThat(main.type()).isEqualTo(PotType.MAIN);
        assertThat(main.index()).isZero();
        assertThat(main.amount()).isEqualTo(300);
        assertThat(main.contributionCap()).isEqualTo(100);
        assertThat(main.contributorUserIds()).containsExactly(1L, 2L, 3L);
        assertThat(main.eligibleWinnerUserIds()).containsExactly(1L, 2L, 3L);
        assertThat(result.sidePots()).isEmpty();
        assertThat(result.uncalledBetReturns()).isEmpty();
    }

    @Test
    void shortAllInCreatesMainAndOneSidePot() {
        PotConstructionResult result = calculate(50, 100, 100);

        assertThat(result.mainPot().orElseThrow().amount()).isEqualTo(150);
        assertThat(result.sidePots()).extracting(Pot::amount).containsExactly(100L);
        assertThat(result.sidePots().getFirst().contributorUserIds()).containsExactly(2L, 3L);
        assertThat(result.sidePots().getFirst().eligibleWinnerUserIds()).containsExactly(2L, 3L);
        assertConserved(result, 250);
    }

    @Test
    void multipleAllInLevelsCreateDeterministicSidePots() {
        PotConstructionResult result = calculate(100, 300, 500, 500);

        assertThat(result.contestedPots()).extracting(Pot::amount)
                .containsExactly(400L, 600L, 400L);
        assertThat(result.contestedPots()).extracting(Pot::contributionCap)
                .containsExactly(100L, 300L, 500L);
        assertThat(result.contestedPots()).extracting(Pot::index)
                .containsExactly(0, 1, 2);
        assertConserved(result, 1_400);
    }

    @Test
    void fourDistinctLevelsAreSlicedExactly() {
        PotConstructionResult result = calculate(50, 120, 300, 500, 500);

        assertThat(result.contestedPots()).extracting(Pot::amount)
                .containsExactly(250L, 280L, 540L, 400L);
        assertThat(result.contestedPots()).extracting(Pot::contributionCap)
                .containsExactly(50L, 120L, 300L, 500L);
        assertConserved(result, 1_470);
    }

    @Test
    void foldedAndLeavingPlayersContributeButCannotWinAnyReachedLayer() {
        PokerPlayer active = player(1, 1, 100, PokerPlayerState.ACTIVE);
        PokerPlayer folded = player(2, 2, 300, PokerPlayerState.FOLDED);
        PokerPlayer leaving = new PokerPlayer(
                3, 3, 0, 0, 300, PokerPlayerState.FOLDED, List.of(), true, true);

        PotConstructionResult result = PotBuilder.calculate(List.of(leaving, folded, active));

        assertThat(result.mainPot().orElseThrow().contributorUserIds()).containsExactly(1L, 2L, 3L);
        assertThat(result.mainPot().orElseThrow().eligibleWinnerUserIds()).containsExactly(1L);
        assertThat(result.sidePots().getFirst().contributorUserIds()).containsExactly(2L, 3L);
        assertThat(result.sidePots().getFirst().eligibleWinnerUserIds()).isEmpty();
        assertConserved(result, 700);
    }

    @Test
    void disconnectedActiveAndAllInPlayersRemainEligibleWhileDisconnectedFoldedDoesNot() {
        PokerPlayer active = player(1, 1, 100, PokerPlayerState.ACTIVE);
        PokerPlayer allIn = player(2, 2, 100, PokerPlayerState.ALL_IN);
        PokerPlayer folded = player(3, 3, 100, PokerPlayerState.FOLDED);
        active.markDisconnected();
        allIn.markDisconnected();
        folded.markDisconnected();

        Pot main = PotBuilder.calculate(List.of(folded, allIn, active)).mainPot().orElseThrow();

        assertThat(main.eligibleWinnerUserIds()).containsExactly(1L, 2L);
        assertThat(main.contributorUserIds()).containsExactly(1L, 2L, 3L);
    }

    @Test
    void unmatchedFinalLayerIsReturnedAndNeverBecomesOnePlayerSidePot() {
        PotConstructionResult result = calculate(100, 300, 500);

        assertThat(result.mainPot().orElseThrow().amount()).isEqualTo(300);
        assertThat(result.sidePots()).extracting(Pot::amount).containsExactly(400L);
        assertThat(result.uncalledBetReturns())
                .containsExactly(new UncalledBetReturn(3, 200));
        assertConserved(result, 900);
    }

    @Test
    void singlePositiveCommitmentIsEntirelyUncalledAndCreatesNoZeroOrContestedPot() {
        PotConstructionResult result = calculate(0, 0, 100);

        assertThat(result.mainPot()).isEmpty();
        assertThat(result.sidePots()).isEmpty();
        assertThat(result.uncalledBetReturns()).containsExactly(new UncalledBetReturn(3, 100));
    }

    @Test
    void zeroCommitmentsCreateNoPotAndNoReturn() {
        PotConstructionResult result = calculate(0, 0, 0);

        assertThat(result.mainPot()).isEmpty();
        assertThat(result.sidePots()).isEmpty();
        assertThat(result.uncalledBetReturns()).isEmpty();
    }

    @Test
    void potAndResultCollectionsAreImmutable() {
        PotConstructionResult result = calculate(50, 100, 100);
        Pot main = result.mainPot().orElseThrow();

        assertThatThrownBy(() -> main.contributorUserIds().add(9L))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> main.eligibleWinnerUserIds().add(9L))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.sidePots().add(main))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.uncalledBetReturns().add(new UncalledBetReturn(1, 1)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void inputAndContributorOrderingIsSeatDeterministic() {
        PokerPlayer seatThree = player(30, 3, 100, PokerPlayerState.ACTIVE);
        PokerPlayer seatOne = player(10, 1, 100, PokerPlayerState.ACTIVE);
        PokerPlayer seatTwo = player(20, 2, 100, PokerPlayerState.ACTIVE);
        List<PokerPlayer> supplied = new ArrayList<>(List.of(seatThree, seatOne, seatTwo));

        Pot main = PotBuilder.calculate(supplied).mainPot().orElseThrow();

        assertThat(main.contributorUserIds()).containsExactly(10L, 20L, 30L);
        assertThat(supplied).containsExactly(seatThree, seatOne, seatTwo);
    }

    @Test
    void calculationDoesNotDeductOrMutateCommittedChipsAgain() {
        PokerPlayer first = new PokerPlayer(1, 1, 900, 0, 100, PokerPlayerState.ACTIVE, List.of());
        PokerPlayer second = new PokerPlayer(2, 2, 800, 0, 200, PokerPlayerState.ACTIVE, List.of());
        PokerPlayer third = new PokerPlayer(3, 3, 800, 0, 200, PokerPlayerState.ACTIVE, List.of());

        PotBuilder.calculate(List.of(first, second, third));

        assertThat(List.of(first, second, third)).extracting(PokerPlayer::tableChips)
                .containsExactly(900L, 800L, 800L);
        assertThat(List.of(first, second, third)).extracting(PokerPlayer::totalCommitted)
                .containsExactly(100L, 200L, 200L);
    }

    @Test
    void arithmeticOverflowIsRejected() {
        PokerPlayer first = player(1, 1, Long.MAX_VALUE, PokerPlayerState.ALL_IN);
        PokerPlayer second = player(2, 2, Long.MAX_VALUE, PokerPlayerState.ALL_IN);

        assertThatThrownBy(() -> PotBuilder.calculate(List.of(first, second)))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void rejectsNullPlayersAndDuplicateAuthoritativeIdentities() {
        PokerPlayer first = player(1, 1, 10, PokerPlayerState.ACTIVE);

        assertThatThrownBy(() -> PotBuilder.calculate(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PotBuilder.calculate(java.util.Arrays.asList(first, null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("null");
        assertThatThrownBy(() -> PotBuilder.calculate(List.of(first, player(1, 2, 10, PokerPlayerState.ACTIVE))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicate userId");
        assertThatThrownBy(() -> PotBuilder.calculate(List.of(first, player(2, 1, 10, PokerPlayerState.ACTIVE))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicate seatNumber");
    }

    @Test
    void compactContributionMatrixAlwaysConservesChips() {
        long[] values = {0, 10, 50, 100, 250, 500};
        for (long first : values) {
            for (long second : values) {
                for (long third : values) {
                    PotConstructionResult result = calculate(first, second, third);
                    assertConserved(result, Math.addExact(Math.addExact(first, second), third));
                }
            }
        }
    }

    private static PotConstructionResult calculate(long... commitments) {
        List<PokerPlayer> players = new ArrayList<>();
        for (int index = 0; index < commitments.length; index++) {
            PokerPlayerState state = commitments[index] > 0
                    ? PokerPlayerState.ALL_IN
                    : PokerPlayerState.ACTIVE;
            players.add(player(index + 1L, index + 1, commitments[index], state));
        }
        return PotBuilder.calculate(players);
    }

    private static PokerPlayer player(
            long userId,
            int seat,
            long totalCommitted,
            PokerPlayerState state) {
        return new PokerPlayer(userId, seat, 0, 0, totalCommitted, state, List.of());
    }

    private static void assertConserved(PotConstructionResult result, long expected) {
        assertThat(Math.addExact(result.contestedAmount(), result.uncalledAmount())).isEqualTo(expected);
    }
}
