package com.propbuy;

import com.propbuy.model.Property;
import com.propbuy.model.ResaleListing;
import com.propbuy.model.User;
import com.propbuy.repo.ListingRepository;
import com.propbuy.repo.PropertyRepository;
import com.propbuy.repo.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.math.BigDecimal;

@Configuration
@Profile("demo")
public class DemoDataConfig {
    @Bean
    CommandLineRunner demoData(UserRepository users, PropertyRepository properties, ListingRepository listings,
                               JdbcTemplate db, PasswordEncoder encoder) {
        return args -> {
            createJdbcTables(db);
            var seller = new User();
            seller.setName("Demo Seller");
            seller.setEmail("seller@propbuy.demo");
            seller.setPasswordHash(encoder.encode("DemoPass123!"));
            seller.setRole(User.Role.SELLER);
            seller.setVerified(true);
            users.save(seller);

            saveUser(users, encoder, "Demo Buyer", "buyer@propbuy.demo", User.Role.BUYER);
            saveUser(users, encoder, "Demo Admin", "admin@propbuy.demo", User.Role.ADMIN);

            createListing(seller, properties, listings, "Sunny 2BHK in Indiranagar", "Bengaluru", "Apartment", 2, "78.00");
            createListing(seller, properties, listings, "Quiet family home in Baner", "Pune", "Villa", 3, "125.00");
            createListing(seller, properties, listings, "Modern city apartment", "Hyderabad", "Apartment", 2, "92.00");
        };
    }

    private void saveUser(UserRepository users, PasswordEncoder encoder, String name, String email, User.Role role) {
        var user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPasswordHash(encoder.encode("DemoPass123!"));
        user.setRole(role);
        user.setVerified(true);
        users.save(user);
    }

    private void createJdbcTables(JdbcTemplate db) {
        db.execute("CREATE TABLE IF NOT EXISTS favorites (user_id BIGINT NOT NULL, listing_id BIGINT NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (user_id, listing_id))");
        db.execute("CREATE TABLE IF NOT EXISTS property_images (id BIGINT AUTO_INCREMENT PRIMARY KEY, property_id BIGINT NOT NULL, storage_key VARCHAR(255) NOT NULL, original_name VARCHAR(255) NOT NULL, content_type VARCHAR(100) NOT NULL, size_bytes BIGINT NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        db.execute("CREATE TABLE IF NOT EXISTS email_otps (id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, otp_hash VARCHAR(255) NOT NULL, expires_at TIMESTAMP NOT NULL, used_at TIMESTAMP NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        db.execute("CREATE TABLE IF NOT EXISTS password_reset_tokens (id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, token_hash VARCHAR(64) NOT NULL UNIQUE, expires_at TIMESTAMP NOT NULL, used BOOLEAN NOT NULL DEFAULT FALSE, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        db.execute("CREATE TABLE IF NOT EXISTS audit_logs (id BIGINT AUTO_INCREMENT PRIMARY KEY, entity_type VARCHAR(80) NOT NULL, entity_id BIGINT NOT NULL, action VARCHAR(80) NOT NULL, actor_id BIGINT NULL, details VARCHAR(1000), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        db.execute("CREATE TABLE IF NOT EXISTS notifications (id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, notification_type VARCHAR(60) NOT NULL, message VARCHAR(500) NOT NULL, read_at TIMESTAMP NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        db.execute("CREATE TABLE IF NOT EXISTS property_visits (id BIGINT AUTO_INCREMENT PRIMARY KEY, listing_id BIGINT NOT NULL, buyer_id BIGINT NOT NULL, scheduled_at TIMESTAMP NOT NULL, status VARCHAR(30) NOT NULL DEFAULT 'REQUESTED', admin_note VARCHAR(500), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        db.execute("CREATE TABLE IF NOT EXISTS saved_searches (id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, name VARCHAR(120) NOT NULL, query_text VARCHAR(255), city VARCHAR(120), bedrooms INT, max_price DECIMAL(18,2), property_type VARCHAR(60), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        db.execute("CREATE TABLE IF NOT EXISTS recently_viewed (user_id BIGINT NOT NULL, listing_id BIGINT NOT NULL, viewed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(user_id, listing_id))");
        db.execute("CREATE TABLE IF NOT EXISTS property_verification_checks (id BIGINT AUTO_INCREMENT PRIMARY KEY, property_id BIGINT NOT NULL, check_type VARCHAR(60) NOT NULL, status VARCHAR(30) NOT NULL DEFAULT 'PENDING', notes VARCHAR(1000), reviewed_by BIGINT NULL, reviewed_at TIMESTAMP NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, UNIQUE(property_id, check_type))");
    }

    private void createListing(User seller, PropertyRepository properties, ListingRepository listings,
                               String title, String city, String type, int bedrooms, String priceLakhs) {
        var property = new Property();
        property.setSeller(seller);
        property.setTitle(title);
        property.setDescription("A demo listing for local development and UI testing.");
        property.setPropertyType(type);
        property.setCity(city);
        property.setArea(BigDecimal.valueOf(bedrooms * 650L));
        property.setBedrooms(bedrooms);
        property.setBathrooms(Math.max(1, bedrooms - 1));
        property.setWorkflowStatus("PUBLISHED");
        property = properties.save(property);

        var listing = new ResaleListing();
        listing.setProperty(property);
        listing.setAskingPrice(new BigDecimal(priceLakhs).multiply(BigDecimal.valueOf(100000)));
        listing.setStatus("PUBLISHED");
        listings.save(listing);
    }
}
