package com.propbuy.web;

import com.propbuy.model.User;
import com.propbuy.repo.PropertyRepository;
import com.propbuy.repo.UserRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final PropertyRepository properties;
    private final UserRepository users;
    private final JdbcTemplate db;

    ReportController(PropertyRepository properties, UserRepository users, JdbcTemplate db) {
        this.properties = properties; this.users = users; this.db = db;
    }

    @GetMapping(value = "/properties/{propertyId}", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<byte[]> propertyReport(@PathVariable Long propertyId, Authentication auth) throws Exception {
        var property = properties.findById(propertyId).orElseThrow();
        var requester = users.findByEmailIgnoreCase(auth.getName()).orElseThrow();
        if (requester.getRole() != User.Role.ADMIN && !property.getSeller().getId().equals(requester.getId())) throw new org.springframework.security.access.AccessDeniedException("You cannot download this report.");
        var lines = new ArrayList<String>();
        lines.add("PropBuy Property Report");
        lines.add("Indicative report - not a legal appraisal or guaranteed sale price");
        lines.add("");
        lines.add("Title: " + safe(property.getTitle()));
        lines.add("City: " + safe(property.getCity()));
        lines.add("Type: " + safe(property.getPropertyType()));
        lines.add("Area: " + safe(property.getArea()) + " sq ft");
        lines.add("Bedrooms / bathrooms: " + safe(property.getBedrooms()) + " / " + safe(property.getBathrooms()));
        lines.add("Workflow status: " + safe(property.getWorkflowStatus()));
        lines.add("Estimated range: " + safe(property.getEstimatedLow()) + " - " + safe(property.getEstimatedHigh()));
        lines.add("");
        lines.add("Verification checklist:");
        db.queryForList("SELECT check_type,status,notes FROM property_verification_checks WHERE property_id=? ORDER BY check_type", propertyId).forEach(row -> lines.add("- " + row.get("check_type") + ": " + row.get("status") + " " + safe(row.get("notes"))));
        lines.add("");
        lines.add("Acquisition offers:");
        db.queryForList("SELECT amount,status,valid_until FROM acquisition_offers WHERE property_id=? ORDER BY id", propertyId).forEach(row -> lines.add("- " + row.get("amount") + " / " + row.get("status") + " / valid until " + row.get("valid_until")));
        lines.add("");
        lines.add("Activity history:");
        db.queryForList("SELECT action,details,created_at FROM audit_logs WHERE entity_type='PROPERTY' AND entity_id=? ORDER BY created_at", propertyId).forEach(row -> lines.add("- " + row.get("created_at") + " " + row.get("action") + " " + safe(row.get("details"))));

        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            var page = new PDPage(); document.addPage(page);
            try (var content = new PDPageContentStream(document, page)) {
                content.beginText(); content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10); content.setLeading(14); content.newLineAtOffset( fifty(), 750);
                for (var line : lines) { content.showText(clean(line)); content.newLine(); }
                content.endText();
            }
            document.save(output);
            var headers = new HttpHeaders();
            headers.setContentDisposition(ContentDisposition.attachment().filename("propbuy-property-" + propertyId + ".pdf").build());
            return ResponseEntity.ok().headers(headers).contentType(MediaType.APPLICATION_PDF).body(output.toByteArray());
        }
    }

    private float fifty() { return 50f; }
    private String safe(Object value) { return value == null ? "Not provided" : value.toString(); }
    private String clean(String value) { return value.replaceAll("[^\\x20-\\x7E]", "?"); }
}
