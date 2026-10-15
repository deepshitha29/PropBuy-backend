CREATE TABLE property_images (id BIGINT PRIMARY KEY AUTO_INCREMENT, property_id BIGINT NOT NULL, storage_key VARCHAR(255) NOT NULL, original_name VARCHAR(255) NOT NULL, content_type VARCHAR(100) NOT NULL, size_bytes BIGINT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY(property_id) REFERENCES properties(id));
CREATE TABLE email_otps (id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL, otp_hash VARCHAR(255) NOT NULL, expires_at TIMESTAMP NOT NULL, used_at TIMESTAMP NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY(user_id) REFERENCES users(id));
CREATE INDEX idx_property_images_property ON property_images(property_id);
CREATE INDEX idx_email_otps_user ON email_otps(user_id, expires_at);
