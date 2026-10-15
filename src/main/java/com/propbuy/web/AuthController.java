package com.propbuy.web;

import com.propbuy.model.User;
import com.propbuy.repo.UserRepository;
import com.propbuy.security.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthController(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    record Register(@NotBlank @Size(max = 120) String name, @Email @NotBlank @Size(max = 255) String email,
                    @NotBlank @Size(min = 8, max = 72) String password) {}
    record Login(@Email @NotBlank String email, @NotBlank String password) {}

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody Register r) {
        if (users.findByEmailIgnoreCase(r.email()).isPresent()) return ResponseEntity.status(409).body(Map.of("error", "Email already registered"));
        var u = new User();
        u.setName(r.name());
        u.setEmail(r.email().toLowerCase());
        u.setPasswordHash(encoder.encode(r.password()));
        u.setRole(User.Role.BUYER);
        u.setVerified(true);
        users.save(u);
        return ResponseEntity.status(201).body(Map.of("verificationRequired", false, "user", u.getEmail()));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody Login r) {
        var u = users.findByEmailIgnoreCase(r.email()).orElse(null);
        if (u == null || !encoder.matches(r.password(), u.getPasswordHash())) return ResponseEntity.status(401).body(Map.of("error", "Invalid email or password"));
        return ResponseEntity.ok(Map.of("token", jwt.create(u.getEmail(), u.getRole().name()), "user", u.getEmail(), "role", u.getRole()));
    }
}
