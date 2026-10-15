package com.propbuy.web;

import com.propbuy.model.Property;
import com.propbuy.repo.PropertyRepository;
import com.propbuy.repo.UserRepository;
import com.propbuy.service.WorkflowActivityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

@RestController
@RequestMapping("/api/properties")
public class PropertyController {
    private final PropertyRepository props;
    private final UserRepository users;
    private final WorkflowActivityService activity;

    PropertyController(PropertyRepository props, UserRepository users, WorkflowActivityService activity) {
        this.props = props; this.users = users; this.activity = activity;
    }

    record Create(@NotBlank @Size(max = 180) String title, @NotBlank String description,
                  @NotBlank @Size(max = 40) String propertyType, @NotBlank @Size(max = 80) String city,
                  String locality, String address, String postalCode, @Positive BigDecimal area,
                  @PositiveOrZero Integer bedrooms, @PositiveOrZero Integer bathrooms, String furnishing,
                  @PositiveOrZero Integer propertyAge, String amenities, @Positive BigDecimal expectedPrice,
                  @Size(max = 1000) String imageUrl) {}

    @PostMapping
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public Property create(@Valid @RequestBody Create r, Authentication auth) {
        var seller = users.findByEmailIgnoreCase(auth.getName()).orElseThrow();
        var p = new Property();
        p.setSeller(seller); p.setTitle(r.title()); p.setDescription(r.description()); p.setPropertyType(r.propertyType());
        p.setCity(r.city()); p.setLocality(r.locality()); p.setAddress(r.address()); p.setPostalCode(r.postalCode());
        p.setArea(r.area()); p.setBedrooms(r.bedrooms()); p.setBathrooms(r.bathrooms()); p.setFurnishing(r.furnishing());
        p.setPropertyAge(r.propertyAge()); p.setAmenities(r.amenities()); p.setExpectedPrice(r.expectedPrice());
        p.setImageUrl(validateImageUrl(r.imageUrl()));
        p.setWorkflowStatus("SUBMITTED");
        p = props.save(p);
        activity.record("PROPERTY", p.getId(), "SUBMITTED", seller.getId(), "Property submitted for review.");
        return p;
    }

    private String validateImageUrl(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            var uri = new URI(value.trim());
            if (!List.of("http", "https").contains(uri.getScheme().toLowerCase()) || uri.getHost() == null) {
                throw new IllegalArgumentException("Image URL must be a valid http or https URL.");
            }
            return uri.toString();
        } catch (URISyntaxException | NullPointerException ex) {
            throw new IllegalArgumentException("Image URL must be a valid http or https URL.");
        }
    }

    @GetMapping("/mine")
    @PreAuthorize("isAuthenticated()")
    public List<Property> mine(Authentication auth) { return props.findBySellerId(users.findByEmailIgnoreCase(auth.getName()).orElseThrow().getId()); }

    @GetMapping("/review")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Property> review() { return props.findByWorkflowStatus("SUBMITTED"); }

    @GetMapping("/offer-queue")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Property> offerQueue() { return props.findByWorkflowStatus("VALUATION_COMPLETED"); }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Property> admin(@RequestParam(required = false) String status,
                                @RequestParam(required = false) String city,
                                @RequestParam(required = false) String propertyType) {
        return props.searchAdmin(blankToNull(status), blankToNull(city), blankToNull(propertyType));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Property one(@PathVariable Long id, Authentication auth) {
        var property = props.findById(id).orElseThrow();
        var user = users.findByEmailIgnoreCase(auth.getName()).orElseThrow();
        if (user.getRole() != com.propbuy.model.User.Role.ADMIN && !property.getSeller().getId().equals(user.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("You cannot view this property.");
        }
        return property;
    }

    @PutMapping("/{id}/review")
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public Property review(@PathVariable Long id, @RequestParam boolean approve,
                           @RequestParam(defaultValue = "false") boolean requestInfo,
                           @RequestParam(required = false) String note, Authentication auth) {
        var p = props.findById(id).orElseThrow();
        var admin = users.findByEmailIgnoreCase(auth.getName()).orElseThrow();
        if (approve && requestInfo) throw new IllegalStateException("A review cannot approve and request information at the same time.");
        String explanation = note == null || note.isBlank() ? "Review rejected; no estimate was issued." : note.trim();
        String action;
        if (requestInfo) {
            p.setWorkflowStatus("NEEDS_INFORMATION");
            action = "INFORMATION_REQUESTED";
            explanation = "Additional information requested: " + explanation;
        } else if (approve) {
            p.setWorkflowStatus("VALUATION_COMPLETED");
            action = "VALUATION_COMPLETED";
            var baseRate = rateFor(p.getPropertyType());
            var cityMultiplier = metroCity(p.getCity()) ? new BigDecimal("1.10") : BigDecimal.ONE;
            var ageMultiplier = p.getPropertyAge() == null ? BigDecimal.ONE : BigDecimal.ONE.subtract(BigDecimal.valueOf(Math.min(p.getPropertyAge(), 30)).multiply(new BigDecimal(".005")));
            var furnishingMultiplier = "Furnished".equalsIgnoreCase(p.getFurnishing()) ? new BigDecimal("1.08") : BigDecimal.ONE;
            var estimate = p.getArea().multiply(baseRate).multiply(cityMultiplier).multiply(ageMultiplier).multiply(furnishingMultiplier).setScale(2, RoundingMode.HALF_UP);
            p.setEstimatedLow(estimate.multiply(new BigDecimal(".90")).setScale(2, RoundingMode.HALF_UP));
            p.setEstimatedHigh(estimate.multiply(new BigDecimal("1.10")).setScale(2, RoundingMode.HALF_UP));
            explanation = "Estimate = area × base rate (" + baseRate + ") × city factor (" + cityMultiplier + ") × age factor (" + ageMultiplier + ") × furnishing factor (" + furnishingMultiplier + "). Range is ±10%; indicative only.";
        } else {
            p.setWorkflowStatus("REJECTED");
            action = "REJECTED";
        }
        p = props.save(p);
        activity.record("PROPERTY", p.getId(), action, admin.getId(), explanation);
        activity.notifyUser(p.getSeller().getId(), action, requestInfo ? "More information is needed for your property submission." : approve ? "Your indicative property valuation is ready." : "Your property submission was not approved.");
        return p;
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private BigDecimal rateFor(String type) {
        if (type == null) return new BigDecimal("5500");
        return switch (type.trim().toLowerCase()) {
            case "villa" -> new BigDecimal("8000");
            case "house" -> new BigDecimal("7000");
            case "plot" -> new BigDecimal("4500");
            default -> new BigDecimal("6500");
        };
    }

    private boolean metroCity(String city) {
        return city != null && List.of("Bengaluru", "Bangalore", "Hyderabad", "Pune", "Mumbai", "Delhi", "Gurugram", "Chennai").stream().anyMatch(city::equalsIgnoreCase);
    }
}
