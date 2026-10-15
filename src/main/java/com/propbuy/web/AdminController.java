package com.propbuy.web;

import com.propbuy.model.User;
import com.propbuy.repo.ListingRepository;
import com.propbuy.repo.PropertyRepository;
import com.propbuy.repo.UserRepository;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final UserRepository users;
    private final JdbcTemplate db;

    AdminController(UserRepository users, PropertyRepository ignoredProperties, ListingRepository ignoredListings, JdbcTemplate db) {
        this.users = users;
        this.db = db;
    }

    @GetMapping("/stats")
    Map<String, Object> stats(@RequestParam(required = false) LocalDate from,
                              @RequestParam(required = false) LocalDate to) {
        return analytics(from, to);
    }

    @GetMapping(value = "/analytics", produces = MediaType.APPLICATION_JSON_VALUE)
    Map<String, Object> analytics(@RequestParam(required = false) LocalDate from,
                                  @RequestParam(required = false) LocalDate to) {
        DateWindow window = new DateWindow(from, to);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", Map.of("from", from == null ? "" : from.toString(), "to", to == null ? "" : to.toString()));
        result.put("users", users.count());
        result.put("sellers", users.countByRole(User.Role.SELLER));
        result.put("buyers", users.countByRole(User.Role.BUYER));
        result.put("submittedProperties", count("properties", "created_at", "workflow_status = ?", List.of("SUBMITTED"), window));
        result.put("awaitingReview", count("properties", "created_at", "workflow_status IN (?, ?)", List.of("SUBMITTED", "UNDER_REVIEW"), window));
        result.put("valuationsCompleted", count("properties", "created_at", "estimated_low IS NOT NULL", List.of(), window));
        result.put("acquiredProperties", count("properties", "created_at", "workflow_status = ?", List.of("ACQUIRED"), window));
        result.put("offersIssued", count("acquisition_offers", null, "status IN (?, ?, ?, ?)", List.of("ISSUED", "ACCEPTED", "DECLINED", "REVISED"), window));
        result.put("activeListings", count("resale_listings", "published_at", "status = ?", List.of("PUBLISHED"), window));
        result.put("pendingPurchaseRequests", count("purchase_requests", "created_at", "status IN (?, ?, ?)", List.of("SUBMITTED", "UNDER_REVIEW", "NEGOTIATION"), window));
        result.put("completedAcquisitions", count("properties", "created_at", "workflow_status = ?", List.of("ACQUIRED"), window));
        result.put("workflowBreakdown", grouped("properties", "workflow_status", "created_at", window));
        result.put("purchaseRequestBreakdown", grouped("purchase_requests", "status", "created_at", window));
        return result;
    }

    @GetMapping(value = "/analytics.csv", produces = "text/csv")
    ResponseEntity<byte[]> analyticsCsv(@RequestParam(required = false) LocalDate from,
                                         @RequestParam(required = false) LocalDate to) {
        Map<String, Object> data = analytics(from, to);
        StringBuilder csv = new StringBuilder("metric,value\n");
        data.forEach((key, value) -> {
            if (!(value instanceof Map)) csv.append(csvCell(key)).append(',').append(csvCell(String.valueOf(value))).append('\n');
        });
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("propbuy-analytics.csv").build().toString())
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private long count(String table, String dateColumn, String predicate, List<Object> predicateArgs, DateWindow window) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ").append(table).append(" WHERE ").append(predicate);
        List<Object> args = new ArrayList<>(predicateArgs);
        window.append(sql, args, dateColumn);
        Long result = db.queryForObject(sql.toString(), Long.class, args.toArray());
        return result == null ? 0 : result;
    }

    private List<Map<String, Object>> grouped(String table, String groupColumn, String dateColumn, DateWindow window) {
        StringBuilder sql = new StringBuilder("SELECT ").append(groupColumn).append(" AS label, COUNT(*) AS metric_value FROM ").append(table);
        List<Object> args = new ArrayList<>();
        window.appendWhere(sql, args, dateColumn);
        sql.append(" GROUP BY ").append(groupColumn).append(" ORDER BY metric_value DESC");
        return db.query(sql.toString(), args.toArray(), (rs, row) -> Map.of("label", rs.getString("label"), "value", rs.getLong("metric_value")));
    }

    private static String csvCell(String value) { return "\"" + value.replace("\"", "\"\"") + "\""; }

    private record DateWindow(LocalDate from, LocalDate to) {
        void append(StringBuilder sql, List<Object> args, String dateColumn) {
            if (dateColumn == null) return;
            if (from != null) { sql.append(" AND ").append(dateColumn).append(" >= ?"); args.add(from.atStartOfDay()); }
            if (to != null) { sql.append(" AND ").append(dateColumn).append(" < ?"); args.add(to.plusDays(1).atStartOfDay()); }
        }
        void appendWhere(StringBuilder sql, List<Object> args, String dateColumn) {
            if (from == null && to == null) return;
            sql.append(" WHERE 1=1");
            if (from != null) { sql.append(" AND ").append(dateColumn).append(" >= ?"); args.add(from.atStartOfDay()); }
            if (to != null) { sql.append(" AND ").append(dateColumn).append(" < ?"); args.add(to.plusDays(1).atStartOfDay()); }
        }
    }
}
