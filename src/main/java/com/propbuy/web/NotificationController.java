package com.propbuy.web;

import com.propbuy.repo.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final JdbcTemplate db;
    private final UserRepository users;

    NotificationController(JdbcTemplate db, UserRepository users) { this.db = db; this.users = users; }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    List<Map<String, Object>> mine(Authentication auth) {
        var userId = users.findByEmailIgnoreCase(auth.getName()).orElseThrow().getId();
        return db.queryForList("SELECT id, notification_type, message, read_at, created_at FROM notifications WHERE user_id=? ORDER BY created_at DESC LIMIT 50", userId);
    }

    @PutMapping("/{id}/read")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    Map<String, Object> read(@PathVariable Long id, Authentication auth) {
        var userId = users.findByEmailIgnoreCase(auth.getName()).orElseThrow().getId();
        var changed = db.update("UPDATE notifications SET read_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=?", id, userId);
        if (changed == 0) throw new IllegalStateException("Notification not found.");
        return Map.of("id", id, "read", true);
    }
}
