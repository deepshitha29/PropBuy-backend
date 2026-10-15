package com.propbuy.web;

import com.propbuy.repo.ListingRepository;
import com.propbuy.repo.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/recommendations")
@PreAuthorize("hasRole('BUYER')")
public class RecommendationController {
    private final ListingRepository listings;
    private final UserRepository users;
    private final JdbcTemplate db;

    RecommendationController(ListingRepository listings, UserRepository users, JdbcTemplate db) {
        this.listings = listings; this.users = users; this.db = db;
    }

    @GetMapping
    List<Map<String, Object>> recommendations(Authentication auth) {
        var userId = users.findByEmailIgnoreCase(auth.getName()).orElseThrow().getId();
        var searches = db.queryForList("SELECT city, bedrooms, max_price, property_type FROM saved_searches WHERE user_id=?", userId);
        var favoriteCities = new HashSet<>(db.queryForList("SELECT p.city FROM favorites f JOIN resale_listings l ON l.id=f.listing_id JOIN properties p ON p.id=l.property_id WHERE f.user_id=?", String.class, userId));
        var candidates = listings.searchPublished(null, null, null, null, null, PageRequest.of(0, 50, Sort.by("publishedAt").descending())).getContent();
        var ranked = new ArrayList<Map<String, Object>>();
        for (var listing : candidates) {
            var score = 0;
            var reasons = new ArrayList<String>();
            var property = listing.getProperty();
            if (favoriteCities.contains(property.getCity())) { score += 2; reasons.add("matches a city from your saved homes"); }
            for (var search : searches) {
                if (same(search.get("city"), property.getCity())) { score += 4; reasons.add("matches your saved location"); }
                if (same(search.get("property_type"), property.getPropertyType())) { score += 3; reasons.add("matches your saved property type"); }
                if (search.get("bedrooms") instanceof Number n && property.getBedrooms() != null && property.getBedrooms().equals(n.intValue())) { score += 2; reasons.add("matches your bedroom preference"); }
                if (search.get("max_price") instanceof Number n && listing.getAskingPrice() != null && listing.getAskingPrice().compareTo(new java.math.BigDecimal(n.toString())) <= 0) { score += 2; reasons.add("fits a saved budget"); }
            }
            if (score == 0) { score = 1; reasons.add("recently published in the available inventory"); }
            ranked.add(Map.of("listing", listing, "score", score, "reason", String.join("; ", new HashSet<>(reasons))));
        }
        ranked.sort(Comparator.comparingInt(item -> -((Number) item.get("score")).intValue()));
        return ranked.stream().limit(12).toList();
    }

    private boolean same(Object first, String second) { return first != null && second != null && first.toString().equalsIgnoreCase(second); }
}
