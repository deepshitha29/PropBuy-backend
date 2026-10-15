package com.propbuy.web;

import com.propbuy.repo.PropertyRepository;
import com.propbuy.repo.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/activity")
public class ActivityController {
    private final PropertyRepository properties;
    private final UserRepository users;
    private final org.springframework.jdbc.core.JdbcTemplate db;

    ActivityController(PropertyRepository properties, UserRepository users, org.springframework.jdbc.core.JdbcTemplate db) {
        this.properties = properties; this.users = users; this.db = db;
    }

    @GetMapping("/properties/{propertyId}")
    @PreAuthorize("isAuthenticated()")
    List<Map<String, Object>> propertyActivity(@PathVariable Long propertyId, Authentication auth) {
        var property = properties.findById(propertyId).orElseThrow();
        var user = users.findByEmailIgnoreCase(auth.getName()).orElseThrow();
        if (user.getRole() != com.propbuy.model.User.Role.ADMIN && !property.getSeller().getId().equals(user.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("You cannot view this activity history.");
        }
        return db.queryForList("SELECT id, entity_type, entity_id, action, details, created_at FROM audit_logs WHERE entity_type='PROPERTY' AND entity_id=? ORDER BY created_at ASC", propertyId);
    }
}
