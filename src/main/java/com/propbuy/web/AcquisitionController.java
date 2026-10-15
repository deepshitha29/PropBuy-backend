package com.propbuy.web;

import com.propbuy.model.AcquisitionOffer;
import com.propbuy.repo.OfferRepository;
import com.propbuy.repo.PropertyRepository;
import com.propbuy.repo.UserRepository;
import com.propbuy.service.WorkflowActivityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/acquisition")
public class AcquisitionController {
    private final OfferRepository offers;
    private final PropertyRepository props;
    private final UserRepository users;
    private final WorkflowActivityService activity;

    AcquisitionController(OfferRepository offers, PropertyRepository props, UserRepository users, WorkflowActivityService activity) {
        this.offers = offers; this.props = props; this.users = users; this.activity = activity;
    }

    record Offer(@Positive BigDecimal amount, @FutureOrPresent LocalDate validUntil, @NotBlank String explanation) {}

    @PostMapping("/properties/{propertyId}/offers")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    AcquisitionOffer issue(@PathVariable Long propertyId, @Valid @RequestBody Offer input, Authentication auth) {
        var p = props.findById(propertyId).orElseThrow();
        if (!Set.of("SUBMITTED", "VALUATION_COMPLETED", "OFFER_ISSUED", "OFFER_DECLINED").contains(p.getWorkflowStatus())) throw new IllegalStateException("Property is not eligible for an offer");
        var admin = users.findByEmailIgnoreCase(auth.getName()).orElseThrow();
        var revised = "OFFER_ISSUED".equals(p.getWorkflowStatus());
        if (revised) {
            offers.findByPropertyId(propertyId).stream().filter(existing -> "ISSUED".equals(existing.getStatus())).forEach(existing -> { existing.setStatus("REVISED"); offers.save(existing); });
        }
        var offer = new AcquisitionOffer();
        offer.setProperty(p); offer.setAdmin(admin); offer.setAmount(input.amount()); offer.setValidUntil(input.validUntil()); offer.setExplanation(input.explanation());
        p.setWorkflowStatus("OFFER_ISSUED");
        props.save(p);
        offer = offers.save(offer);
        activity.record("PROPERTY", propertyId, revised ? "OFFER_REVISED" : "OFFER_ISSUED", admin.getId(), "Acquisition offer " + (revised ? "revised" : "issued") + ": " + input.amount());
        activity.notifyUser(p.getSeller().getId(), "OFFER_ISSUED", "A new acquisition offer is ready for your property.");
        return offer;
    }

    @GetMapping("/mine")
    @PreAuthorize("isAuthenticated()")
    List<AcquisitionOffer> mine(Authentication auth) { return offers.findByPropertySellerId(users.findByEmailIgnoreCase(auth.getName()).orElseThrow().getId()); }

    @PutMapping("/offers/{id}/respond")
    @PreAuthorize("isAuthenticated()")
    @Transactional
    AcquisitionOffer respond(@PathVariable Long id, @RequestParam boolean accept, Authentication auth) {
        var offer = offers.findById(id).orElseThrow();
        var seller = users.findByEmailIgnoreCase(auth.getName()).orElseThrow();
        if (!offer.getProperty().getSeller().getEmail().equalsIgnoreCase(auth.getName())) throw new org.springframework.security.access.AccessDeniedException("Not your offer");
        if (!"ISSUED".equals(offer.getStatus()) || offer.getValidUntil().isBefore(LocalDate.now())) throw new IllegalStateException("Offer is not eligible for a response");
        offer.setStatus(accept ? "ACCEPTED" : "DECLINED");
        offer.getProperty().setWorkflowStatus(accept ? "ACQUISITION_IN_PROGRESS" : "OFFER_DECLINED");
        props.save(offer.getProperty());
        offer = offers.save(offer);
        activity.record("PROPERTY", offer.getProperty().getId(), accept ? "OFFER_ACCEPTED" : "OFFER_DECLINED", seller.getId(), "Seller responded to acquisition offer.");
        return offer;
    }

    @PutMapping("/properties/{id}/acquired")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    com.propbuy.model.Property acquired(@PathVariable Long id, Authentication auth) {
        var p = props.findById(id).orElseThrow();
        if (!"ACQUISITION_IN_PROGRESS".equals(p.getWorkflowStatus())) throw new IllegalStateException("Seller must accept an offer first");
        p.setWorkflowStatus("ACQUIRED");
        var saved = props.save(p);
        activity.record("PROPERTY", id, "ACQUISITION_COMPLETED", users.findByEmailIgnoreCase(auth.getName()).orElseThrow().getId(), "Property acquisition completed.");
        activity.notifyUser(p.getSeller().getId(), "ACQUISITION_COMPLETED", "Your property acquisition has been completed.");
        return saved;
    }
}
