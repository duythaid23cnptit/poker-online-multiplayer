package com.ptit.poker.auth.infrastructure.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpaqueRefreshTokenServiceTests {

    private final OpaqueRefreshTokenService service = new OpaqueRefreshTokenService();

    @Test
    void generatesDistinctOpaqueTokensAndStableHashes() {
        String first = service.generate();
        String second = service.generate();

        assertThat(first).isNotBlank().isNotEqualTo(second);
        assertThat(service.hash(first)).hasSize(64).isEqualTo(service.hash(first));
        assertThat(service.hash(first)).isNotEqualTo(first);
    }
}

