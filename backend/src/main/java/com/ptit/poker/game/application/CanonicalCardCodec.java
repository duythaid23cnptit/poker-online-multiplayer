package com.ptit.poker.game.application;

import com.ptit.poker.game.domain.card.Card;
import com.ptit.poker.game.domain.card.Rank;
import com.ptit.poker.game.domain.card.Suit;
import java.util.List;

public final class CanonicalCardCodec {
    private CanonicalCardCodec() {}
    public static String serialize(List<Card> cards) { return cards.stream().map(CanonicalCardCodec::serialize).collect(java.util.stream.Collectors.joining(",")); }
    public static String serialize(Card card) { return rank(card.rank()) + suit(card.suit()); }
    public static List<Card> deserialize(String encoded) {
        if (encoded == null || encoded.isEmpty()) return List.of();
        return java.util.Arrays.stream(encoded.split(",", -1)).map(CanonicalCardCodec::deserializeCard).toList();
    }
    private static Card deserializeCard(String code) {
        if (code.length() != 2) throw new IllegalArgumentException("invalid canonical card code");
        Rank rank = switch (code.charAt(0)) {
            case '2' -> Rank.TWO; case '3' -> Rank.THREE; case '4' -> Rank.FOUR; case '5' -> Rank.FIVE;
            case '6' -> Rank.SIX; case '7' -> Rank.SEVEN; case '8' -> Rank.EIGHT; case '9' -> Rank.NINE;
            case 'T' -> Rank.TEN; case 'J' -> Rank.JACK; case 'Q' -> Rank.QUEEN; case 'K' -> Rank.KING; case 'A' -> Rank.ACE;
            default -> throw new IllegalArgumentException("invalid canonical card rank");
        };
        Suit suit = switch (code.charAt(1)) {
            case 'C' -> Suit.CLUBS; case 'D' -> Suit.DIAMONDS; case 'H' -> Suit.HEARTS; case 'S' -> Suit.SPADES;
            default -> throw new IllegalArgumentException("invalid canonical card suit");
        };
        return new Card(rank, suit);
    }
    private static String rank(Rank rank) { return "23456789TJQKA".substring(rank.strength() - 2, rank.strength() - 1); }
    private static String suit(Suit suit) { return switch (suit) { case CLUBS -> "C"; case DIAMONDS -> "D"; case HEARTS -> "H"; case SPADES -> "S"; }; }
}
