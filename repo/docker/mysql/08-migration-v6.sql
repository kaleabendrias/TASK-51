-- Migration V6: Server-side search terms store and address uniqueness constraint
USE booking_portal;

-- =====================================================
-- SEARCH TERMS: global popular terms ranked by frequency/recency
-- =====================================================
CREATE TABLE search_terms (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    term          VARCHAR(255) NOT NULL UNIQUE,
    frequency     INT          NOT NULL DEFAULT 1,
    last_used_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_search_terms_popular ON search_terms(frequency DESC, last_used_at DESC);

-- =====================================================
-- ADDRESS DEFAULT UNIQUENESS: only one default per user
-- =====================================================
-- Strategy: use a BEFORE INSERT and BEFORE UPDATE trigger to enforce
-- at most one is_default=true per user_id. MySQL unique indexes treat
-- NULL as distinct, so a generated-column approach cannot reliably
-- prevent multiple defaults. Triggers provide a deterministic guarantee.

DELIMITER //

CREATE TRIGGER trg_address_default_insert
BEFORE INSERT ON addresses
FOR EACH ROW
BEGIN
    IF NEW.is_default = TRUE THEN
        IF (SELECT COUNT(*) FROM addresses
            WHERE user_id = NEW.user_id AND is_default = TRUE) > 0 THEN
            SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Another default address already exists for this user';
        END IF;
    END IF;
END//

CREATE TRIGGER trg_address_default_update
BEFORE UPDATE ON addresses
FOR EACH ROW
BEGIN
    IF NEW.is_default = TRUE AND (OLD.is_default = FALSE OR OLD.is_default IS NULL) THEN
        IF (SELECT COUNT(*) FROM addresses
            WHERE user_id = NEW.user_id AND is_default = TRUE AND id != NEW.id) > 0 THEN
            SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Another default address already exists for this user';
        END IF;
    END IF;
END//

DELIMITER ;

INSERT INTO schema_version (version, description)
VALUES (6, 'Search terms store, address default uniqueness triggers');
