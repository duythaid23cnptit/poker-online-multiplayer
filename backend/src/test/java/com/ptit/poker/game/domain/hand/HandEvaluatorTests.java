package com.ptit.poker.game.domain.hand;

import com.ptit.poker.game.domain.card.Card;
import com.ptit.poker.game.domain.card.Rank;
import com.ptit.poker.game.domain.card.Suit;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HandEvaluatorTests {

    private final HandEvaluator evaluator = new HandEvaluator();

    @Test
    void classifiesEveryStandardCategory() {
        assertCategory(HandCategory.HIGH_CARD, "AS", "KD", "9C", "5H", "3S");
        assertCategory(HandCategory.ONE_PAIR, "KS", "KD", "AC", "9H", "4S");
        assertCategory(HandCategory.TWO_PAIR, "AS", "AD", "TC", "TH", "KS");
        assertCategory(HandCategory.THREE_OF_A_KIND, "QS", "QD", "QC", "9H", "4S");
        assertCategory(HandCategory.STRAIGHT, "9S", "8D", "7C", "6H", "5S");
        assertCategory(HandCategory.FLUSH, "AS", "JS", "9S", "5S", "2S");
        assertCategory(HandCategory.FULL_HOUSE, "QS", "QD", "QC", "4H", "4S");
        assertCategory(HandCategory.FOUR_OF_A_KIND, "9S", "9D", "9C", "9H", "AS");
        assertCategory(HandCategory.STRAIGHT_FLUSH, "9S", "8S", "7S", "6S", "5S");
        assertCategory(HandCategory.ROYAL_FLUSH, "AS", "KS", "QS", "JS", "TS");
    }

    @Test
    void highCardComparisonUsesEveryKickerInOrder() {
        assertStronger(hand("AS", "KD", "9C", "5H", "3S"), hand("KS", "QD", "JC", "9H", "8S"));
        assertStronger(hand("AS", "KD", "9C", "5H", "3S"), hand("AH", "KC", "8D", "7S", "6C"));
        assertStronger(hand("AS", "KD", "9C", "5H", "3S"), hand("AH", "KC", "9D", "4S", "3C"));
        assertStronger(hand("AS", "KD", "9C", "5H", "3S"), hand("AH", "KC", "9D", "5S", "2C"));
    }

    @Test
    void onePairComparisonUsesPairThenAllThreeKickers() {
        assertStronger(hand("AS", "AD", "KC", "QH", "9S"), hand("KS", "KD", "AC", "QH", "JS"));
        assertStronger(hand("AS", "AD", "KC", "QH", "9S"), hand("AH", "AC", "QD", "JS", "TS"));
        assertStronger(hand("AS", "AD", "KC", "QH", "9S"), hand("AH", "AC", "KD", "JS", "TS"));
        assertStronger(hand("AS", "AD", "KC", "QH", "9S"), hand("AH", "AC", "KD", "QS", "8C"));
    }

    @Test
    void twoPairComparisonUsesHighPairLowPairThenKicker() {
        assertStronger(hand("AS", "AD", "KC", "KH", "2S"), hand("QS", "QD", "JC", "JH", "AS"));
        assertStronger(hand("AS", "AD", "KC", "KH", "2S"), hand("AH", "AC", "QD", "QS", "KS"));
        assertStronger(hand("AS", "AD", "KC", "KH", "9S"), hand("AH", "AC", "KD", "KS", "8C"));
    }

    @Test
    void threeOfAKindComparisonUsesTripsThenBothKickers() {
        assertStronger(hand("AS", "AD", "AC", "KH", "2S"), hand("KS", "KD", "KC", "AH", "QS"));
        assertStronger(hand("QS", "QD", "QC", "AH", "9S"), hand("QH", "QC", "QS", "KD", "JS"));
        assertStronger(hand("QS", "QD", "QC", "AH", "9S"), hand("QH", "QC", "QS", "AD", "8S"));
    }

    @Test
    void recognizesBroadwayWheelAndNormalStraights() {
        assertThat(hand("TS", "JD", "QC", "KH", "AS"))
                .isEqualTo(new HandValue(HandCategory.STRAIGHT, List.of(14)));
        assertThat(hand("AS", "2D", "3C", "4H", "5S"))
                .isEqualTo(new HandValue(HandCategory.STRAIGHT, List.of(5)));
        assertThat(hand("9S", "8D", "7C", "6H", "5S"))
                .isEqualTo(new HandValue(HandCategory.STRAIGHT, List.of(9)));
    }

    @Test
    void sixHighStraightBeatsWheelAndDuplicateRankDoesNotCreateStraight() {
        assertStronger(hand("6S", "5D", "4C", "3H", "2S"), hand("AS", "2D", "3C", "4H", "5S"));
        assertThat(hand("6S", "5D", "4C", "3H", "3S").category()).isEqualTo(HandCategory.ONE_PAIR);
    }

    @Test
    void flushComparisonUsesAllRanksButNeverSuit() {
        assertStronger(hand("AS", "JS", "9S", "5S", "3S"), hand("KS", "QS", "JS", "9S", "8S"));
        assertStronger(hand("AS", "JS", "9S", "5S", "3S"), hand("AH", "JH", "8H", "7H", "6H"));
        assertStronger(hand("AS", "JS", "9S", "5S", "3S"), hand("AH", "JH", "9H", "4H", "3H"));
        assertStronger(hand("AS", "JS", "9S", "5S", "3S"), hand("AH", "JH", "9H", "5H", "2H"));
        assertThat(hand("AS", "JS", "9S", "5S", "3S"))
                .isEqualTo(hand("AH", "JH", "9H", "5H", "3H"));
    }

    @Test
    void fullHouseComparisonUsesTripsThenPair() {
        assertStronger(hand("AS", "AD", "AC", "KH", "KS"), hand("KS", "KD", "KC", "AH", "AS"));
        assertStronger(hand("QS", "QD", "QC", "9H", "9S"), hand("QH", "QC", "QS", "8D", "8S"));
    }

    @Test
    void fourOfAKindComparisonUsesQuadsThenKicker() {
        assertStronger(hand("AS", "AD", "AC", "AH", "2S"), hand("KS", "KD", "KC", "KH", "AS"));
        assertStronger(hand("9S", "9D", "9C", "9H", "AS"), hand("9S", "9D", "9C", "9H", "KS"));
    }

    @Test
    void straightFlushSupportsWheelAndComparesByHighCard() {
        assertThat(hand("AS", "2S", "3S", "4S", "5S"))
                .isEqualTo(new HandValue(HandCategory.STRAIGHT_FLUSH, List.of(5)));
        assertStronger(hand("9S", "8S", "7S", "6S", "5S"), hand("8H", "7H", "6H", "5H", "4H"));
    }

    @Test
    void royalFlushesOfDifferentSuitsTie() {
        HandValue spades = hand("AS", "KS", "QS", "JS", "TS");
        HandValue hearts = hand("AH", "KH", "QH", "JH", "TH");

        assertThat(spades.category()).isEqualTo(HandCategory.ROYAL_FLUSH);
        assertThat(spades.compareTo(hearts)).isZero();
        assertThat(spades).isEqualTo(hearts);
    }

    @Test
    void categoryHierarchyUsesExplicitStrength() {
        List<HandValue> ascending = List.of(
                hand("AS", "KD", "9C", "5H", "3S"),
                hand("AS", "AD", "KC", "9H", "3S"),
                hand("AS", "AD", "KC", "KH", "3S"),
                hand("AS", "AD", "AC", "KH", "3S"),
                hand("9S", "8D", "7C", "6H", "5S"),
                hand("AS", "JS", "9S", "5S", "3S"),
                hand("AS", "AD", "AC", "KH", "KS"),
                hand("9S", "9D", "9C", "9H", "AS"),
                hand("9S", "8S", "7S", "6S", "5S"),
                hand("AS", "KS", "QS", "JS", "TS"));

        for (int index = 1; index < ascending.size(); index++) {
            assertThat(ascending.get(index)).isGreaterThan(ascending.get(index - 1));
        }
        assertThat(HandCategory.values()).extracting(HandCategory::strength)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
    }

    @Test
    void bestOfSevenChoosesBestTwoPairFromThreePairs() {
        assertThat(best("AS", "AD", "KC", "KH", "QS", "QD", "2C"))
                .isEqualTo(new HandValue(HandCategory.TWO_PAIR, List.of(14, 13, 12)));
    }

    @Test
    void evaluateBestSupportsFiveAndSixCards() {
        assertThat(best("AS", "AD", "KC", "QH", "9S"))
                .isEqualTo(hand("AS", "AD", "KC", "QH", "9S"));
        assertThat(best("AS", "AD", "KC", "QH", "9S", "8D"))
                .isEqualTo(new HandValue(HandCategory.ONE_PAIR, List.of(14, 13, 12, 9)));
    }

    @Test
    void bestOfSevenChoosesBestFullHouseFromTwoTrips() {
        assertThat(best("AS", "AD", "AC", "KS", "KD", "KC", "QH"))
                .isEqualTo(new HandValue(HandCategory.FULL_HOUSE, List.of(14, 13)));
    }

    @Test
    void fourSuitedCardsDoNotProduceFlush() {
        assertThat(best("AS", "JS", "9S", "5S", "3D", "2C", "KH").category())
                .isNotEqualTo(HandCategory.FLUSH);
    }

    @Test
    void sixSuitedCardsChooseTheTopFiveForFlush() {
        assertThat(best("AS", "JS", "9S", "5S", "3S", "2S", "KH"))
                .isEqualTo(new HandValue(HandCategory.FLUSH, List.of(14, 11, 9, 5, 3)));
    }

    @Test
    void multipleStraightsChooseTheHighestIncludingOverWheel() {
        assertThat(best("9S", "8D", "7C", "6H", "5S", "4D", "3C"))
                .isEqualTo(new HandValue(HandCategory.STRAIGHT, List.of(9)));
        assertThat(best("AS", "2D", "3C", "4H", "5S", "6D", "KC"))
                .isEqualTo(new HandValue(HandCategory.STRAIGHT, List.of(6)));
    }

    @Test
    void boardCanBeTheBestHandWithoutUsingEitherHoleCard() {
        HandValue firstPlayer = best("2C", "3D", "AS", "KS", "QS", "JS", "TS");
        HandValue secondPlayer = best("9C", "9D", "AS", "KS", "QS", "JS", "TS");

        assertThat(firstPlayer).isEqualTo(new HandValue(HandCategory.ROYAL_FLUSH, List.of()));
        assertThat(firstPlayer.compareTo(secondPlayer)).isZero();
    }

    @Test
    void bestHandMayUseExactlyOneOrBothConceptualHoleCards() {
        assertThat(best("AS", "2D", "KS", "QS", "JS", "TS", "3C").category())
                .isEqualTo(HandCategory.ROYAL_FLUSH);
        assertThat(best("AS", "AD", "AC", "AH", "KS", "2D", "3C"))
                .isEqualTo(new HandValue(HandCategory.FOUR_OF_A_KIND, List.of(14, 13)));
    }

    @Test
    void bestFullHouseUsesStrongestTripsAndAvailablePair() {
        assertThat(best("KS", "KD", "KC", "QS", "QD", "JS", "JD"))
                .isEqualTo(new HandValue(HandCategory.FULL_HOUSE, List.of(13, 12)));
    }

    @Test
    void bestOfSevenChoosesHighestStraightFlush() {
        assertThat(best("9S", "8S", "7S", "6S", "5S", "4S", "KC"))
                .isEqualTo(new HandValue(HandCategory.STRAIGHT_FLUSH, List.of(9)));
    }

    @Test
    void bestOfSevenUsesHighestKickerWithEqualQuads() {
        assertThat(best("9S", "9D", "9C", "9H", "AS", "KS", "2C"))
                .isEqualTo(new HandValue(HandCategory.FOUR_OF_A_KIND, List.of(9, 14)));
    }

    @Test
    void equalRanksInDifferentSuitsProduceEqualHandValues() {
        assertThat(hand("AS", "KD", "9C", "5H", "3S"))
                .isEqualTo(hand("AH", "KC", "9D", "5S", "3C"));
    }

    @Test
    void evaluateFiveRejectsNullWrongSizeNullCardAndDuplicateCard() {
        Card aceSpades = card("AS");

        assertThatThrownBy(() -> evaluator.evaluateFive(null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("cards");
        assertThatThrownBy(() -> evaluator.evaluateFive(cards("AS", "KS", "QS", "JS")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("5 and 5");
        assertThatThrownBy(() -> evaluator.evaluateFive(cards("AS", "KS", "QS", "JS", "TS", "9S")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("5 and 5");
        assertThatThrownBy(() -> evaluator.evaluateFive(List.of(
                aceSpades, card("KS"), card("QS"), card("JS"), aceSpades)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicates");
        List<Card> withNull = new ArrayList<>(cards("AS", "KS", "QS", "JS", "TS"));
        withNull.set(2, null);
        assertThatThrownBy(() -> evaluator.evaluateFive(withNull))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("null");
    }

    @Test
    void evaluateBestRejectsNullFewerThanFiveMoreThanSevenAndDuplicates() {
        assertThatThrownBy(() -> evaluator.evaluateBest(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> evaluator.evaluateBest(cards("AS", "KS", "QS", "JS")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> evaluator.evaluateBest(cards("AS", "KS", "QS", "JS", "TS", "9S", "8S", "7S")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> evaluator.evaluateBest(cards("AS", "KS", "QS", "JS", "TS", "AS")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicates");
    }

    @Test
    void evaluationDoesNotMutateInputAndHandValueDefensivelyCopiesTieBreakers() {
        List<Card> input = new ArrayList<>(cards("AS", "KD", "9C", "5H", "3S"));
        List<Card> original = List.copyOf(input);
        List<Integer> mutableTieBreakers = new ArrayList<>(List.of(14, 13, 9, 5, 3));

        evaluator.evaluateFive(input);
        HandValue value = new HandValue(HandCategory.HIGH_CARD, mutableTieBreakers);
        mutableTieBreakers.clear();

        assertThat(input).containsExactlyElementsOf(original);
        assertThat(value.tieBreakValues()).containsExactly(14, 13, 9, 5, 3);
        assertThatThrownBy(() -> value.tieBreakValues().add(2))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private void assertCategory(HandCategory category, String... cardCodes) {
        assertThat(hand(cardCodes).category()).isEqualTo(category);
    }

    private void assertStronger(HandValue stronger, HandValue weaker) {
        assertThat(stronger).isGreaterThan(weaker);
    }

    private HandValue hand(String... cardCodes) {
        return evaluator.evaluateFive(cards(cardCodes));
    }

    private HandValue best(String... cardCodes) {
        return evaluator.evaluateBest(cards(cardCodes));
    }

    private static List<Card> cards(String... cardCodes) {
        return java.util.Arrays.stream(cardCodes).map(HandEvaluatorTests::card).toList();
    }

    private static Card card(String code) {
        Rank rank = switch (code.charAt(0)) {
            case '2' -> Rank.TWO;
            case '3' -> Rank.THREE;
            case '4' -> Rank.FOUR;
            case '5' -> Rank.FIVE;
            case '6' -> Rank.SIX;
            case '7' -> Rank.SEVEN;
            case '8' -> Rank.EIGHT;
            case '9' -> Rank.NINE;
            case 'T' -> Rank.TEN;
            case 'J' -> Rank.JACK;
            case 'Q' -> Rank.QUEEN;
            case 'K' -> Rank.KING;
            case 'A' -> Rank.ACE;
            default -> throw new IllegalArgumentException("Unknown test rank: " + code);
        };
        Suit suit = switch (code.charAt(1)) {
            case 'C' -> Suit.CLUBS;
            case 'D' -> Suit.DIAMONDS;
            case 'H' -> Suit.HEARTS;
            case 'S' -> Suit.SPADES;
            default -> throw new IllegalArgumentException("Unknown test suit: " + code);
        };
        return new Card(rank, suit);
    }
}
