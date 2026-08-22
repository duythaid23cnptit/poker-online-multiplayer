package com.ptit.poker.auth.infrastructure.security;

import com.ptit.poker.auth.infrastructure.persistence.UserEntity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
@Profile("!bootstrap")
public class JwtService {

    private final JwtProperties properties;
    private final SecretKey signingKey;
    private final Clock clock;

    @Autowired
    public JwtService(JwtProperties properties) {
        this(properties, Clock.systemUTC());
    }

    JwtService(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
        this.clock = clock;
    }

    public String createAccessToken(UserEntity user) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.accessTokenExpiration());
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("type", "access")
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    public Long parseAccessTokenSubject(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (!"access".equals(claims.get("type", String.class))) {
            throw new IllegalArgumentException("Unexpected token type");
        }
        return Long.valueOf(claims.getSubject());
    }

    public long accessTokenExpiresInSeconds() {
        return properties.accessTokenExpiration().toSeconds();
    }

    public Instant refreshTokenExpiresAt() {
        return clock.instant().plus(properties.refreshTokenExpiration());
    }
}
