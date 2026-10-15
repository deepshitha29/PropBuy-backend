package com.propbuy.web;

import com.propbuy.repo.ListingRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/search")
public class SearchController {
    private static final int MAX_PAGE_SIZE = 50;
    private static final Set<String> SORT_FIELDS = Set.of("askingPrice", "publishedAt");

    private final ListingRepository listings;

    SearchController(ListingRepository listings) {
        this.listings = listings;
    }

    @GetMapping
    public Map<String, Object> search(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Integer bedrooms,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String propertyType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "publishedAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        if (page < 0) {
            throw new IllegalStateException("Page must be zero or greater.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalStateException("Page size must be between 1 and " + MAX_PAGE_SIZE + ".");
        }
        if (!SORT_FIELDS.contains(sort)) {
            throw new IllegalStateException("Unsupported sort field.");
        }
        if (!("asc".equalsIgnoreCase(direction) || "desc".equalsIgnoreCase(direction))) {
            throw new IllegalStateException("Sort direction must be asc or desc.");
        }
        if (bedrooms != null && bedrooms < 0) {
            throw new IllegalStateException("Bedrooms cannot be negative.");
        }
        if (maxPrice != null && maxPrice.signum() < 0) {
            throw new IllegalStateException("Maximum price cannot be negative.");
        }

        var ordering = "asc".equalsIgnoreCase(direction)
                ? Sort.by(sort).ascending()
                : Sort.by(sort).descending();
        var results = listings.searchPublished(
                normalize(q), normalize(city), bedrooms, maxPrice, normalize(propertyType),
                PageRequest.of(page, size, ordering));

        return Map.of(
                "query", q,
                "filters", Map.of(
                        "city", city == null ? "" : city,
                        "bedrooms", bedrooms == null ? "" : bedrooms,
                        "maxPrice", maxPrice == null ? "" : maxPrice,
                        "propertyType", propertyType == null ? "" : propertyType),
                "results", results.getContent(),
                "page", Map.of(
                        "number", results.getNumber(),
                        "size", results.getSize(),
                        "totalElements", results.getTotalElements(),
                        "totalPages", results.getTotalPages(),
                        "hasNext", results.hasNext()));
    }

    @GetMapping("/conversational")
    public Map<String, Object> conversational(@RequestParam String q,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "12") int size) {
        var text = q == null ? "" : q.trim();
        var lower = text.toLowerCase();
        var bedroomMatch = Pattern.compile("(\\d+)\\s*(?:-?bedroom|bhk)").matcher(lower);
        Integer bedrooms = bedroomMatch.find() ? Integer.valueOf(bedroomMatch.group(1)) : null;
        var cityMatch = Pattern.compile("\\b(?:in|at|near)\\s+([a-z][a-z ]+?)(?=\\s+(?:under|below|with|for)\\b|$)").matcher(lower);
        String city = cityMatch.find() ? cityMatch.group(1).trim() : null;
        BigDecimal maxPrice = null;
        var priceMatch = Pattern.compile("(?:under|below)\\s+(\\d+(?:\\.\\d+)?)\\s*(crore|cr|lakh|lakhs)?").matcher(lower);
        if (priceMatch.find()) {
            var amount = new BigDecimal(priceMatch.group(1));
            var unit = priceMatch.group(2);
            maxPrice = unit == null ? amount : amount.multiply(unit.startsWith("cro") || unit.equals("cr") ? new BigDecimal("10000000") : new BigDecimal("100000"));
        }
        String propertyType = null;
        if (lower.contains("apartment") || lower.contains("flat")) propertyType = "Apartment";
        else if (lower.contains("villa")) propertyType = "Villa";
        else if (lower.contains("house")) propertyType = "House";
        if (bedrooms == null && city == null && maxPrice == null && propertyType == null) {
            throw new IllegalStateException("Try a search like '2 bedroom apartments in Hyderabad under 80 lakh'.");
        }
        var result = search("", city, bedrooms, maxPrice, propertyType, page, size, "publishedAt", "desc");
        return Map.of("interpretation", Map.of("city", city == null ? "" : city, "bedrooms", bedrooms == null ? "" : bedrooms, "maxPrice", maxPrice == null ? "" : maxPrice, "propertyType", propertyType == null ? "" : propertyType), "results", result.get("results"), "page", result.get("page"));
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
