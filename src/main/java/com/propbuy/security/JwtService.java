package com.propbuy.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {
    private static final int MINIMUM_SECRET_BYTES = 32;

    private final SecretKey signingKey;
    private final long expiry;

    public JwtService(@Value("${app.jwt-secret}") String secret, @Value("${app.jwt-expiration-ms}") long expiry) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("APP_JWT_SECRET must be provided before the application can start.");
        }

        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalStateException("APP_JWT_SECRET must contain at least 32 UTF-8 bytes.");
        }

        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
        this.expiry = expiry;
    }

    public String create(String email, String role) {
        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiry))
                .signWith(signingKey)
                .compact();
    }

    public Jws<Claims> parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token);
    }
}
