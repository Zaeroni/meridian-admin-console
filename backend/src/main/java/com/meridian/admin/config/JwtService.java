package com.meridian.admin.config;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies signed tokens. The token only proves who the caller is (subject = reviewer id).
 * Role and active status are read from the database on every request, so a role change or
 * deactivation takes effect immediately instead of waiting for the token to expire.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration lifetime;
    private final Clock clock;

    public JwtService(JwtProperties properties, Clock clock) {
        byte[] secret = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("meridian.jwt.secret must be at least 32 bytes long");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.lifetime = Duration.ofMinutes(properties.expirationMinutes());
        this.clock = clock;
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }

    public IssuedToken issue(UUID reviewerId) {
        Instant now = clock.instant();
        Instant expires = now.plus(lifetime);
        String token = Jwts.builder()
                .subject(reviewerId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expires))
                .signWith(key)
                .compact();
        return new IssuedToken(token, expires);
    }

    /** Returns the reviewer id if the token is well formed, correctly signed and not expired. */
    public Optional<UUID> verify(String token) {
        try {
            var claims = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(UUID.fromString(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
