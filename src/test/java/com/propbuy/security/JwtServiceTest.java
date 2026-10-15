package com.propbuy.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {
    private static final String SECRET = "test-secret-that-is-at-least-32-bytes-long";

    @Test
    void createsAndParsesSignedToken() {
        var service = new JwtService(SECRET, 60_000);

        var token = service.create("buyer@example.com", "BUYER");
        var claims = service.parse(token).getPayload();

        assertEquals("buyer@example.com", claims.getSubject());
        assertEquals("BUYER", claims.get("role"));
    }

    @Test
    void rejectsMissingSecret() {
        var error = assertThrows(IllegalStateException.class, () -> new JwtService("", 60_000));

        assertEquals("APP_JWT_SECRET must be provided before the application can start.", error.getMessage());
    }

    @Test
    void rejectsShortSecret() {
        var error = assertThrows(IllegalStateException.class, () -> new JwtService("too-short", 60_000));

        assertEquals("APP_JWT_SECRET must contain at least 32 UTF-8 bytes.", error.getMessage());
    }
}
