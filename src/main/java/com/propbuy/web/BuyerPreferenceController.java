package com.propbuy.web;

import com.propbuy.repo.ListingRepository;
import com.propbuy.repo.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.sql.Statement;

@RestController
@RequestMapping("/api/buyer")
@PreAuthorize("hasRole('BUYER')")
public class BuyerPreferenceController {
    private final JdbcTemplate db;
    private final UserRepository users;
    private final ListingRepository listings;

    BuyerPreferenceController(JdbcTemplate db, UserRepository users, ListingRepository listings) {
        this.db = db; this.users = users; this.listings = listings;
    }

    record SavedSearch(@NotBlank @Size(max = 120) String name, @Size(max = 255) String query, String city,
                       Integer bedrooms, BigDecimal maxPrice, String propertyType) {}

    @GetMapping("/saved-searches")
    List<Map<String, Object>> savedSearches(Authentication auth) {
        var userId = users.findByEmailIgnoreCase(auth.getName()).orElseThrow().getId();
        return db.queryForList("SELECT id, name, query_text, city, bedrooms, max_price, property_type, created_at FROM saved_searches WHERE user_id=? ORDER BY created_at DESC", userId);
    }

    @PostMapping("/saved-searches")
    @Transactional
    Map<String, Object> saveSearch(@Valid @RequestBody SavedSearch input, Authentication auth) {
        if (input.bedrooms() != null && input.bedrooms() < 0) throw new IllegalStateException("Bedrooms cannot be negative.");
        if (input.maxPrice() != null && input.maxPrice().signum() < 0) throw new IllegalStateException("Maximum price cannot be negative.");
        var userId = users.findByEmailIgnoreCase(auth.getName()).orElseThrow().getId();
        var keyHolder = new GeneratedKeyHolder();
        db.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO saved_searches(user_id,name,query_text,city,bedrooms,max_price,property_type) VALUES (?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, userId); statement.setString(2, input.name()); statement.setString(3, input.query()); statement.setString(4, input.city());
            if (input.bedrooms() == null) statement.setObject(5, null); else statement.setInt(5, input.bedrooms());
            if (input.maxPrice() == null) statement.setObject(6, null); else statement.setBigDecimal(6, input.maxPrice());
            statement.setString(7, input.propertyType());
            return statement;
        }, keyHolder);
        var generatedKeys = keyHolder.getKeys();
        var generatedId = generatedKeys.get("ID");
        if (generatedId == null) generatedId = generatedKeys.get("id");
        if (generatedId == null) generatedId = generatedKeys.values().iterator().next();
        var id = ((Number) generatedId).longValue();
        return Map.of("id", id, "name", input.name());
    }

    @DeleteMapping("/saved-searches/{id}")
    @Transactional
    Map<String, Object> deleteSearch(@PathVariable Long id, Authentication auth) {
        var userId = users.findByEmailIgnoreCase(auth.getName()).orElseThrow().getId();
        if (db.update("DELETE FROM saved_searches WHERE id=? AND user_id=?", id, userId) == 0) throw new IllegalStateException("Saved search not found.");
        return Map.of("deleted", true, "id", id);
    }

    @PostMapping("/recently-viewed/{listingId}")
    @Transactional
    Map<String, Object> viewed(@PathVariable Long listingId, Authentication auth) {
        listings.findById(listingId).orElseThrow();
        var userId = users.findByEmailIgnoreCase(auth.getName()).orElseThrow().getId();
        if (db.update("UPDATE recently_viewed SET viewed_at=CURRENT_TIMESTAMP WHERE user_id=? AND listing_id=?", userId, listingId) == 0) {
            db.update("INSERT INTO recently_viewed(user_id,listing_id,viewed_at) VALUES (?,?,CURRENT_TIMESTAMP)", userId, listingId);
        }
        return Map.of("saved", true, "listingId", listingId);
    }

    @GetMapping("/recently-viewed")
    List<Map<String, Object>> recentlyViewed(Authentication auth) {
        var userId = users.findByEmailIgnoreCase(auth.getName()).orElseThrow().getId();
        return db.queryForList("SELECT r.listing_id, r.viewed_at, p.title, p.city, l.asking_price FROM recently_viewed r JOIN resale_listings l ON l.id=r.listing_id JOIN properties p ON p.id=l.property_id WHERE r.user_id=? ORDER BY r.viewed_at DESC LIMIT 20", userId);
    }
}
