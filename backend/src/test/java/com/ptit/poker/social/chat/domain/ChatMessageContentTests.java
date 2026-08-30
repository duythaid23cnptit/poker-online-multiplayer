package com.ptit.poker.social.chat.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatMessageContentTests {
    @Test void trimsEdgesAndPreservesOrdinaryInternalWhitespace() {
        assertThat(new ChatMessageContent("  Xin  chào 👋  ").value()).isEqualTo("Xin  chào 👋");
    }

    @Test void rejectsNullBlankAndControlCharacters() {
        assertThatThrownBy(() -> new ChatMessageContent(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ChatMessageContent(" \u2003 ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChatMessageContent("line one\nline two")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void countsUnicodeCodePointsRatherThanUtf16Units() {
        assertThat(new ChatMessageContent("😀".repeat(500)).value().codePointCount(0, 1000)).isEqualTo(500);
        assertThatThrownBy(() -> new ChatMessageContent("😀".repeat(501)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("500 Unicode code points");
    }
}
