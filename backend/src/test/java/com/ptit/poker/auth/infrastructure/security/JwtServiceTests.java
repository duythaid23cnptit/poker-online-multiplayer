package com.ptit.poker.auth.infrastructure.security;

import com.ptit.poker.auth.infrastructure.persistence.UserEntity;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtServiceTests {

    private static final String SECRET =
            "cG9rZXItb25saW5lLXRlc3Qtc2lnbmluZy1rZXktZm9yLW9ubHktdGVzdHM=";

    @Test
    void createsAndParsesAccessToken() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        JwtService service = new JwtService(
                new JwtProperties(SECRET, Duration.ofMinutes(5), Duration.ofDays(1)), clock);
        UserEntity user = mock(UserEntity.class);
        when(user.getId()).thenReturn(42L);

        String token = service.createAccessToken(user);

        assertThat(service.parseAccessTokenSubject(token)).isEqualTo(42L);
        assertThat(service.accessTokenExpiresInSeconds()).isEqualTo(300);
    }

    @Test
    void rejectsMalformedToken() {
        JwtService service = new JwtService(
                new JwtProperties(SECRET, Duration.ofMinutes(5), Duration.ofDays(1)), Clock.systemUTC());

        assertThatThrownBy(() -> service.parseAccessTokenSubject("not-a-jwt"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredTokenWithoutSleeping() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        JwtService service = new JwtService(
                new JwtProperties(SECRET, Duration.ofSeconds(1), Duration.ofDays(1)), clock);
        UserEntity user = mock(UserEntity.class);
        when(user.getId()).thenReturn(7L);
        String token = service.createAccessToken(user);

        clock.advance(Duration.ofSeconds(2));

        assertThatThrownBy(() -> service.parseAccessTokenSubject(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}

