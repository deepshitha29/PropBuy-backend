CREATE TABLE saved_searches (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    query_text VARCHAR(255),
    city VARCHAR(120),
    bedrooms INT,
    max_price DECIMAL(18,2),
    property_type VARCHAR(60),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE recently_viewed (
    user_id BIGINT NOT NULL,
    listing_id BIGINT NOT NULL,
    viewed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, listing_id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (listing_id) REFERENCES resale_listings(id)
);

CREATE INDEX idx_saved_searches_user ON saved_searches(user_id, created_at);
CREATE INDEX idx_recently_viewed_user ON recently_viewed(user_id, viewed_at);
