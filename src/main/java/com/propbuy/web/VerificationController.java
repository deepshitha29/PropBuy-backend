package com.propbuy.web;

import com.propbuy.model.User;
import com.propbuy.repo.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class VerificationController {
    private final UserRepository users;
    private final JdbcTemplate db;
    private final PasswordEncoder encoder;
    private final boolean devLog;

    VerificationController(UserRepository users, JdbcTemplate db, PasswordEncoder encoder,
                           @Value("${app.otp-dev-log}") boolean devLog) {
        this.users = users;
        this.db = db;
        this.encoder = encoder;
        this.devLog = devLog;
    }

    record EmailRequest(@Email @NotBlank String email) {}
    record Reset(@NotBlank String token, @NotBlank @Size(min = 8, max = 72) String password) {}

    @PostMapping("/forgot-password")
    @Transactional
    public Map<String, String> forgot(@Valid @RequestBody EmailRequest r) {
        users.findByEmailIgnoreCase(r.email()).ifPresent(u -> {
            var token = UUID.randomUUID() + UUID.randomUUID().toString();
            try {
                var digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
                var hash = HexFormat.of().formatHex(digest);
                db.update("UPDATE password_reset_tokens SET used=true WHERE user_id=? AND used=false", u.getId());
                db.update("INSERT INTO password_reset_tokens(user_id,token_hash,expires_at) VALUES (?,?,?)", u.getId(), hash,
                        Timestamp.from(Instant.now().plus(Duration.ofMinutes(30))));
                if (devLog) System.out.println("[DEV ONLY] PropBuy reset token for " + u.getEmail() + ": " + token);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        });
        return Map.of("message", "If the account exists, reset instructions have been issued");
    }

    @PostMapping("/reset-password")
    @Transactional
    public Map<String, Boolean> reset(@Valid @RequestBody Reset r) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(r.token().getBytes(StandardCharsets.UTF_8));
            var hash = HexFormat.of().formatHex(digest);
            var rows = db.query("SELECT id,user_id,expires_at FROM password_reset_tokens WHERE token_hash=? AND used=false LIMIT 1",
                    (rs, n) -> Map.of("id", rs.getLong("id"), "user", rs.getLong("user_id"), "expires", rs.getTimestamp("expires_at").toInstant()), hash);
            if (rows.isEmpty() || Instant.now().isAfter((Instant) rows.get(0).get("expires"))) throw new IllegalStateException("Invalid or expired reset token");
            var u = users.findById((Long) rows.get(0).get("user")).orElseThrow();
            u.setPasswordHash(encoder.encode(r.password()));
            users.save(u);
            db.update("UPDATE password_reset_tokens SET used=true WHERE id=?", rows.get(0).get("id"));
            return Map.of("reset", true);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
