package com.propbuy.web;

import com.propbuy.repo.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserRepository users;
    UserController(UserRepository users) { this.users = users; }

    record Update(@NotBlank @Size(max = 120) String name) {}

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    Object me(Authentication auth) { return users.findByEmailIgnoreCase(auth.getName()).orElseThrow(); }

    @PutMapping("/me")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    Object update(@Valid @RequestBody Update input, Authentication auth) {
        var user = users.findByEmailIgnoreCase(auth.getName()).orElseThrow();
        user.setName(input.name().trim());
        return users.save(user);
    }
}
