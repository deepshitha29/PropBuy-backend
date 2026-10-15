CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    entity_type VARCHAR(80) NOT NULL,
    entity_id BIGINT NOT NULL,
    action VARCHAR(80) NOT NULL,
    actor_id BIGINT NULL,
    details VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (actor_id) REFERENCES users(id)
);

ALTER TABLE purchase_requests
    ADD COLUMN active_request_key VARCHAR(100)
        GENERATED ALWAYS AS (
            CASE
                WHEN status IN ('SUBMITTED', 'UNDER_REVIEW', 'CONTACT_PENDING', 'NEGOTIATION', 'APPROVED')
                THEN CONCAT(buyer_id, ':', listing_id)
                ELSE NULL
            END
        ) STORED,
    ADD UNIQUE KEY uq_purchase_requests_active (active_request_key);

CREATE INDEX idx_properties_city_bedrooms ON properties(city, bedrooms);
CREATE INDEX idx_listings_price_status ON resale_listings(status, asking_price);
CREATE INDEX idx_purchase_requests_listing_status ON purchase_requests(listing_id, status);
CREATE INDEX idx_audit_logs_entity ON audit_logs(entity_type, entity_id, created_at);
