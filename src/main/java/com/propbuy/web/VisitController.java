package com.propbuy.web;

import com.propbuy.repo.ListingRepository;
import com.propbuy.repo.UserRepository;
import com.propbuy.service.WorkflowActivityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.support.GeneratedKeyHolder;

@RestController
@RequestMapping("/api/visits")
public class VisitController {
    private final JdbcTemplate db;
    private final ListingRepository listings;
    private final UserRepository users;
    private final WorkflowActivityService activity;

    VisitController(JdbcTemplate db, ListingRepository listings, UserRepository users, WorkflowActivityService activity) {
        this.db = db; this.listings = listings; this.users = users; this.activity = activity;
    }

    record Create(@Future LocalDateTime scheduledAt) {}
    record Status(@NotBlank String value, String adminNote) {}

    @PostMapping("/listings/{listingId}")
    @Transactional
    @PreAuthorize("hasRole('BUYER')")
    Map<String, Object> request(@PathVariable Long listingId, @Valid @RequestBody Create input, Authentication auth) {
        var listing = listings.findById(listingId).orElseThrow();
        if (!"PUBLISHED".equals(listing.getStatus())) throw new IllegalStateException("Visits are only available for published listings.");
        var buyer = users.findByEmailIgnoreCase(auth.getName()).orElseThrow();
        var scheduled = Timestamp.valueOf(input.scheduledAt());
        var conflict = db.queryForObject("SELECT COUNT(*) FROM property_visits WHERE listing_id=? AND scheduled_at BETWEEN ? AND ? AND status IN ('REQUESTED','CONFIRMED','RESCHEDULED')", Long.class, listingId,
                Timestamp.valueOf(input.scheduledAt().minusMinutes(59)), Timestamp.valueOf(input.scheduledAt().plusMinutes(59)));
        if (conflict != null && conflict > 0) throw new IllegalStateException("That visit time is already requested.");
        var duplicate = db.queryForObject("SELECT COUNT(*) FROM property_visits WHERE listing_id=? AND buyer_id=? AND status IN ('REQUESTED','CONFIRMED','RESCHEDULED')", Long.class, listingId, buyer.getId());
        if (duplicate != null && duplicate > 0) throw new IllegalStateException("You already have an active visit request for this property.");
        var keyHolder = new GeneratedKeyHolder();
        db.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO property_visits(listing_id,buyer_id,scheduled_at) VALUES (?,?,?)", Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, listingId);
            statement.setLong(2, buyer.getId());
            statement.setTimestamp(3, scheduled);
            return statement;
        }, keyHolder);
        var generatedKeys = keyHolder.getKeys();
        var generatedId = generatedKeys.get("ID");
        if (generatedId == null) generatedId = generatedKeys.get("id");
        if (generatedId == null) generatedId = generatedKeys.values().iterator().next();
        var visitId = ((Number) generatedId).longValue();
        activity.record("LISTING", listingId, "VISIT_REQUESTED", buyer.getId(), "Visit requested for " + input.scheduledAt());
        activity.notifyUser(listing.getProperty().getSeller().getId(), "VISIT_REQUESTED", "A buyer requested a visit for " + listing.getProperty().getTitle() + ".");
        return Map.of("id", visitId, "status", "REQUESTED", "scheduledAt", input.scheduledAt());
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('BUYER')")
    List<Map<String, Object>> mine(Authentication auth) {
        var buyerId = users.findByEmailIgnoreCase(auth.getName()).orElseThrow().getId();
        return db.queryForList("SELECT v.id, v.listing_id, p.title, p.city, v.scheduled_at, v.status, v.admin_note, v.created_at FROM property_visits v JOIN resale_listings l ON l.id=v.listing_id JOIN properties p ON p.id=l.property_id WHERE v.buyer_id=? ORDER BY v.scheduled_at DESC", buyerId);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    List<Map<String, Object>> all() {
        return db.queryForList("SELECT v.id, v.listing_id, v.buyer_id, u.email AS buyer_email, p.title, p.city, v.scheduled_at, v.status, v.admin_note, v.created_at FROM property_visits v JOIN users u ON u.id=v.buyer_id JOIN resale_listings l ON l.id=v.listing_id JOIN properties p ON p.id=l.property_id ORDER BY v.scheduled_at ASC");
    }

    @PutMapping("/{id}/status")
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    Map<String, Object> update(@PathVariable Long id, @Valid @RequestBody Status input, Authentication auth) {
        if (!Set.of("REQUESTED", "CONFIRMED", "RESCHEDULED", "CANCELLED", "COMPLETED").contains(input.value())) throw new IllegalStateException("Unsupported visit status.");
        var rows = db.queryForList("SELECT buyer_id, listing_id FROM property_visits WHERE id=?", id);
        if (rows.isEmpty()) throw new IllegalStateException("Visit not found.");
        var buyerId = ((Number) rows.get(0).get("buyer_id")).longValue();
        var listingId = ((Number) rows.get(0).get("listing_id")).longValue();
        var adminId = users.findByEmailIgnoreCase(auth.getName()).orElseThrow().getId();
        db.update("UPDATE property_visits SET status=?, admin_note=? WHERE id=?", input.value(), input.adminNote(), id);
        activity.record("LISTING", listingId, "VISIT_" + input.value(), adminId, input.adminNote());
        activity.notifyUser(buyerId, "VISIT_" + input.value(), "Your property visit is " + input.value().toLowerCase() + ".");
        return Map.of("id", id, "status", input.value());
    }
}
