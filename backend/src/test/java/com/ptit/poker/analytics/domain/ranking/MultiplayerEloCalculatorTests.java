package com.ptit.poker.analytics.domain.ranking;
import static org.assertj.core.api.Assertions.*;import java.util.*;import org.junit.jupiter.api.Test;
class MultiplayerEloCalculatorTests {private final MultiplayerEloCalculator elo=new MultiplayerEloCalculator();
 @Test void equalRatedHeadsUpWinnerLoser(){assertThat(elo.calculate(List.of(r(1,1000,1),r(2,1000,-1)))).extracting(x->x.newRating()).containsExactly(1016,984);}
 @Test void higherRatedFavoriteWins(){var c=elo.calculate(List.of(r(1,1200,1),r(2,1000,-1)));assertThat(c.get(0).ratingDelta()).isBetween(1,15);}
 @Test void lowerRatedUnderdogWins(){assertThat(elo.calculate(List.of(r(1,1200,-1),r(2,1000,1))).get(1).ratingDelta()).isGreaterThan(16);}
 @Test void twoPlayerTie(){var c=elo.calculate(List.of(r(1,1200,0),r(2,1000,0)));assertThat(c.get(0).ratingDelta()).isNegative();assertThat(c.get(1).ratingDelta()).isPositive();}
 @Test void orderedThreePlayerResult(){assertThat(elo.calculate(List.of(r(1,1000,100),r(2,1000,0),r(3,1000,-100)))).extracting(x->x.ratingDelta()).containsExactly(16,0,-16);}
 @Test void threePlayerTieUsesCompetitionPlacement(){assertThat(elo.calculate(List.of(r(1,1000,10),r(2,1000,10),r(3,1000,-20)))).extracting(x->x.placement()).containsExactly(1,1,3);}
 @Test void sixPlayerResult(){assertThat(elo.calculate(results(6))).hasSize(6).allSatisfy(c->assertThat(c.participantCount()).isEqualTo(6));}
 @Test void ninePlayerResult(){assertThat(elo.calculate(results(9))).hasSize(9).allSatisfy(c->assertThat(c.participantCount()).isEqualTo(9));}
 @Test void allPlayersTiedWithDifferentRatingsMayMove(){var c=elo.calculate(List.of(r(1,800,0),r(2,1000,0),r(3,1200,0)));assertThat(c.get(0).ratingDelta()).isPositive();assertThat(c.get(2).ratingDelta()).isNegative();}
 @Test void ratingFloorStoresAppliedDelta(){var c=elo.calculate(List.of(r(1,0,-1),r(2,2000,1))).get(0);assertThat(c.newRating()).isZero();assertThat(c.ratingDelta()).isZero();}
 @Test void simultaneousCalculationUsesAllPreSessionRatings(){assertThat(elo.calculate(List.of(r(1,800,3),r(2,1000,2),r(3,1200,1)))).extracting(x->x.oldRating()).containsExactly(800,1000,1200);}
 @Test void repeatedInputIsExactlyDeterministic(){var input=List.of(r(1,900,3),r(2,1000,2),r(3,1100,1));assertThat(elo.calculate(input)).isEqualTo(elo.calculate(input));}
 @Test void fewerThanTwoParticipantsIsRejected(){assertThatIllegalArgumentException().isThrownBy(()->elo.calculate(List.of(r(1,1000,0))));assertThatIllegalArgumentException().isThrownBy(()->elo.calculate(List.of()));}
 private static MultiplayerEloCalculator.Result r(long id,int rating,long net){return new MultiplayerEloCalculator.Result(id,rating,net);}private static List<MultiplayerEloCalculator.Result> results(int n){List<MultiplayerEloCalculator.Result>x=new ArrayList<>();for(int i=0;i<n;i++)x.add(r(i+1,1000,n-i));return x;}}
