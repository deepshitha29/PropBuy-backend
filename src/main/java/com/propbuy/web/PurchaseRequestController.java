package com.propbuy.web;

import com.propbuy.model.PurchaseRequest;
import com.propbuy.repo.ListingRepository;
import com.propbuy.repo.PurchaseRequestRepository;
import com.propbuy.repo.UserRepository;
import com.propbuy.service.WorkflowActivityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/purchase-requests")
public class PurchaseRequestController {
    private final PurchaseRequestRepository requests;
    private final ListingRepository listings;
    private final UserRepository users;
    private final WorkflowActivityService activity;

    PurchaseRequestController(PurchaseRequestRepository requests, ListingRepository listings, UserRepository users, WorkflowActivityService activity) {
        this.requests = requests; this.listings = listings; this.users = users; this.activity = activity;
    }

    record Create(@Positive BigDecimal proposedPrice, @Size(max = 1000) String message) {}

    @PostMapping("/listings/{listingId}")
    @PreAuthorize("hasRole('BUYER')")
    @Transactional
    public PurchaseRequest create(@PathVariable Long listingId, @Valid @RequestBody Create r, Authentication auth) {
        var listing = listings.findById(listingId).orElseThrow();
        if (!"PUBLISHED".equals(listing.getStatus())) throw new IllegalStateException("Listing is not available");
        var buyer = users.findByEmailIgnoreCase(auth.getName()).orElseThrow();
        if (requests.existsByBuyerIdAndListingIdAndStatusIn(buyer.getId(), listingId, List.of("SUBMITTED", "UNDER_REVIEW", "CONTACT_PENDING", "NEGOTIATION", "APPROVED"))) throw new IllegalStateException("You already have an active request for this listing");
        var pr = new PurchaseRequest();
        pr.setBuyer(buyer); pr.setListing(listing); pr.setProposedPrice(r.proposedPrice()); pr.setMessage(r.message());
        pr = requests.save(pr);
        activity.record("PURCHASE_REQUEST", pr.getId(), "SUBMITTED", buyer.getId(), "Purchase request submitted.");
        activity.notifyUser(listing.getProperty().getSeller().getId(), "PURCHASE_REQUEST", "A buyer submitted a purchase request for " + listing.getProperty().getTitle() + ".");
        return pr;
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('BUYER')")
    public List<PurchaseRequest> mine(Authentication auth) { return requests.findByBuyerId(users.findByEmailIgnoreCase(auth.getName()).orElseThrow().getId()); }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<PurchaseRequest> all() { return requests.findAll(); }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public PurchaseRequest status(@PathVariable Long id, @RequestParam @NotBlank String value, Authentication auth) {
        var request = requests.findById(id).orElseThrow();
        var allowed = Map.of("SUBMITTED", Set.of("UNDER_REVIEW", "REJECTED", "WITHDRAWN"), "UNDER_REVIEW", Set.of("CONTACT_PENDING", "NEGOTIATION", "REJECTED"), "CONTACT_PENDING", Set.of("NEGOTIATION", "REJECTED"), "NEGOTIATION", Set.of("APPROVED", "REJECTED"), "APPROVED", Set.of("COMPLETED", "REJECTED"));
        if (!allowed.getOrDefault(request.getStatus(), Set.of()).contains(value)) throw new IllegalStateException("Invalid purchase-request transition");
        if ("COMPLETED".equals(value) && !"PUBLISHED".equals(request.getListing().getStatus())) throw new IllegalStateException("Only a published listing can be completed.");
        request.setStatus(value);
        if ("COMPLETED".equals(value)) { request.getListing().setStatus("SOLD"); listings.save(request.getListing()); }
        var admin = users.findByEmailIgnoreCase(auth.getName()).orElseThrow();
        request = requests.save(request);
        activity.record("PURCHASE_REQUEST", request.getId(), value, admin.getId(), "Purchase request status changed to " + value + ".");
        activity.notifyUser(request.getBuyer().getId(), "PURCHASE_REQUEST_" + value, "Your purchase request is now " + value.toLowerCase().replace('_', ' ') + ".");
        return request;
    }
}
