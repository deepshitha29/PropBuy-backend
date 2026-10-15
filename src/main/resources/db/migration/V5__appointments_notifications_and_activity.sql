CREATE TABLE notifications (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    notification_type VARCHAR(60) NOT NULL,
    message VARCHAR(500) NOT NULL,
    read_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE property_visits (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    listing_id BIGINT NOT NULL,
    buyer_id BIGINT NOT NULL,
    scheduled_at TIMESTAMP NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'REQUESTED',
    admin_note VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (listing_id) REFERENCES resale_listings(id),
    FOREIGN KEY (buyer_id) REFERENCES users(id)
);

CREATE INDEX idx_notifications_user_created ON notifications(user_id, created_at);
CREATE INDEX idx_visits_listing_time ON property_visits(listing_id, scheduled_at);
CREATE INDEX idx_visits_buyer_created ON property_visits(buyer_id, created_at);
