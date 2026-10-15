CREATE TABLE property_verification_checks (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    property_id BIGINT NOT NULL,
    check_type VARCHAR(60) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    notes VARCHAR(1000),
    reviewed_by BIGINT NULL,
    reviewed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_property_check_type (property_id, check_type),
    FOREIGN KEY (property_id) REFERENCES properties(id),
    FOREIGN KEY (reviewed_by) REFERENCES users(id)
);

CREATE INDEX idx_verification_property_status ON property_verification_checks(property_id, status);
