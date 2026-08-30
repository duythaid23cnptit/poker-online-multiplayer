package com.ptit.poker.social.chat.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientMessageIdTests {
    @Test void parsesAndRendersCanonicalUuid() {
        UUID uuid = UUID.randomUUID();
        assertThat(ClientMessageId.parse(uuid.toString()).toString()).isEqualTo(uuid.toString());
    }

    @Test void rejectsNullAndMalformedValues() {
        assertThatThrownBy(() -> ClientMessageId.parse(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ClientMessageId.parse("not-a-uuid")).isInstanceOf(IllegalArgumentException.class);
    }
}
