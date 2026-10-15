package com.propbuy.web;

import com.propbuy.exception.NotFoundException;
import com.propbuy.model.ResaleListing;
import com.propbuy.repo.ListingRepository;
import com.propbuy.repo.PropertyRepository;
import com.propbuy.repo.UserRepository;
import com.propbuy.service.WorkflowActivityService;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/listings")
public class ListingController {
    private final ListingRepository listings;
    private final PropertyRepository properties;
    private final UserRepository users;
    private final WorkflowActivityService activity;

    ListingController(ListingRepository listings, PropertyRepository properties, UserRepository users, WorkflowActivityService activity) {
        this.listings = listings;
        this.properties = properties;
        this.users = users;
        this.activity = activity;
    }

    @GetMapping
    public List<ResaleListing> all() {
        return listings.findByStatus("PUBLISHED");
    }

    @PostMapping("/{propertyId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResaleListing publish(@PathVariable Long propertyId,
                                 @RequestParam @Positive BigDecimal askingPrice,
                                 org.springframework.security.core.Authentication auth) {
        var property = properties.findById(propertyId).orElseThrow();
        if (!"ACQUIRED".equals(property.getWorkflowStatus())) {
            throw new IllegalStateException("Only acquired properties can be published.");
        }
        if (listings.findByPropertyId(propertyId).isPresent()) {
            throw new IllegalStateException("This property already has a resale listing.");
        }

        var listing = new ResaleListing();
        listing.setProperty(property);
        listing.setAskingPrice(askingPrice);
        listing.setStatus("PUBLISHED");
        listing = listings.save(listing);
        activity.record("PROPERTY", propertyId, "RESALE_PUBLISHED", users.findByEmailIgnoreCase(auth.getName()).orElseThrow().getId(), "Resale listing published.");
        activity.notifyUser(property.getSeller().getId(), "RESALE_PUBLISHED", "Your acquired property is now published for resale.");
        return listing;
    }

    @GetMapping("/{id}")
    public ResaleListing one(@PathVariable Long id) {
        var listing = listings.findById(id).orElseThrow();
        if (!"PUBLISHED".equals(listing.getStatus())) {
            throw new NotFoundException("Listing is not available.");
        }
        return listing;
    }
}
