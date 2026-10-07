package com.nagarik.security;

import com.nagarik.config.JwtProperties;
import com.nagarik.domain.AppUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

/**
 * Issues and verifies the signed tokens that carry identity.
 *
 * The token is the only thing the client holds, so it carries everything needed
 * to describe the caller. It is signed, not encrypted - anyone holding it can
 * read the name and email inside, which is why nothing secret ever goes in.
 *
 * jjwt 0.12.x API: keys are a SecretKey from Keys.hmacShaKeyFor, verification
 * goes through Jwts.parser().verifyWith(key), and signWith(key) infers HS256
 * from the key length. The 0.11.x calls (parserBuilder, setSubject,
 * signWith(key, algorithm)) do not exist here.
 */
@Service
public class JwtService {

    /** HS256 needs a key of at least 256 bits. Anything shorter is rejected by
     *  the library at signing time, which would surface as a 500 on the first
     *  login rather than at startup - so it is checked here instead. */
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final long expiryHours;

    public JwtService(JwtProperties properties) {
        String secret = properties.getSecret() == null ? "" : properties.getSecret();
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "nagarik.jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes (it is "
                            + bytes.length + "). Generate one with: openssl rand -base64 48");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expiryHours = properties.getExpiryHours();
    }

    public String issue(AppUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("name", user.getDisplayName())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiryHours, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }

    /**
     * The account id inside a token, or empty if the token is expired, tampered
     * with, or simply not a token. A failure here is not an error worth logging
     * per request - it is the normal case for a stale browser, and the request
     * carries on unauthenticated, which the route rules then judge.
     */
    public Optional<Long> userId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(Long.valueOf(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public long getExpiryHours() {
        return expiryHours;
    }
}
