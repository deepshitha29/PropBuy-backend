package com.propbuy.web;

import com.propbuy.model.User;
import com.propbuy.repo.PropertyRepository;
import com.propbuy.repo.UserRepository;
import com.propbuy.service.WorkflowActivityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/verification")
public class VerificationChecklistController {
    private static final Set<String> CHECK_TYPES = Set.of("OWNERSHIP_DOCUMENTS", "IDENTITY_DOCUMENTS", "PROPERTY_IMAGES", "PROPERTY_DOCUMENTS");
    private static final Set<String> STATUSES = Set.of("PENDING", "UNDER_REVIEW", "REVIEWED", "NEEDS_INFORMATION");

    private final JdbcTemplate db;
    private final PropertyRepository properties;
    private final UserRepository users;
    private final WorkflowActivityService activity;

    VerificationChecklistController(JdbcTemplate db, PropertyRepository properties, UserRepository users, WorkflowActivityService activity) {
        this.db = db; this.properties = properties; this.users = users; this.activity = activity;
    }

    record Update(@NotBlank String checkType, @NotBlank String status, @Size(max = 1000) String notes) {}

    @GetMapping("/properties/{propertyId}")
    @PreAuthorize("isAuthenticated()")
    List<Map<String, Object>> get(@PathVariable Long propertyId, Authentication auth) {
        var property = properties.findById(propertyId).orElseThrow();
        var user = users.findByEmailIgnoreCase(auth.getName()).orElseThrow();
        if (user.getRole() != User.Role.ADMIN && !property.getSeller().getId().equals(user.getId())) throw new org.springframework.security.access.AccessDeniedException("You cannot view this verification checklist.");
        return db.queryForList("SELECT id, check_type, status, notes, reviewed_by, reviewed_at, created_at, updated_at FROM property_verification_checks WHERE property_id=? ORDER BY check_type", propertyId);
    }

    @PutMapping("/properties/{propertyId}")
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    Map<String, Object> update(@PathVariable Long propertyId, @Valid @RequestBody Update input, Authentication auth) {
        var property = properties.findById(propertyId).orElseThrow();
        if (!CHECK_TYPES.contains(input.checkType())) throw new IllegalStateException("Unsupported verification check type.");
        if (!STATUSES.contains(input.status())) throw new IllegalStateException("Unsupported verification status.");
        var admin = users.findByEmailIgnoreCase(auth.getName()).orElseThrow();
        var existing = db.queryForList("SELECT id FROM property_verification_checks WHERE property_id=? AND check_type=?", propertyId, input.checkType());
        if (existing.isEmpty()) {
            db.update("INSERT INTO property_verification_checks(property_id,check_type,status,notes,reviewed_by,reviewed_at,updated_at) VALUES (?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", propertyId, input.checkType(), input.status(), input.notes(), admin.getId());
        } else {
            db.update("UPDATE property_verification_checks SET status=?,notes=?,reviewed_by=?,reviewed_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE property_id=? AND check_type=?", input.status(), input.notes(), admin.getId(), propertyId, input.checkType());
        }
        activity.record("PROPERTY", propertyId, "VERIFICATION_" + input.status(), admin.getId(), input.checkType() + (input.notes() == null ? "" : ": " + input.notes()));
        activity.notifyUser(property.getSeller().getId(), "VERIFICATION_" + input.status(), "Verification update: " + input.checkType().toLowerCase().replace('_', ' ') + " is " + input.status().toLowerCase().replace('_', ' ') + ".");
        return Map.of("propertyId", propertyId, "checkType", input.checkType(), "status", input.status());
    }
}
