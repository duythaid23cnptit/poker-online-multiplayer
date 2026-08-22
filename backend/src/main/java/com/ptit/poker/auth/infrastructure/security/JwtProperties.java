package com.ptit.poker.auth.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(String secret, Duration accessTokenExpiration, Duration refreshTokenExpiration) {

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("JWT secret must be configured");
        }
        if (accessTokenExpiration == null || accessTokenExpiration.isNegative()
                || accessTokenExpiration.isZero()) {
            throw new IllegalArgumentException("Access token expiration must be positive");
        }
        if (refreshTokenExpiration == null || refreshTokenExpiration.isNegative()
                || refreshTokenExpiration.isZero()) {
            throw new IllegalArgumentException("Refresh token expiration must be positive");
        }
    }
}

