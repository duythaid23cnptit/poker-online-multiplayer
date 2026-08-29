package com.ptit.poker.social.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FriendshipPairTests {

    @Test
    void canonicalizesEitherDirection() {
        assertThat(FriendshipPair.of(9, 3)).isEqualTo(new FriendshipPair(3, 9));
        assertThat(FriendshipPair.of(3, 9)).isEqualTo(new FriendshipPair(3, 9));
    }

    @Test
    void rejectsSelfPair() {
        assertThatThrownBy(() -> FriendshipPair.of(7, 7))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveUserIds() {
        assertThatThrownBy(() -> FriendshipPair.of(0, 7))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
