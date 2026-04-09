-- Migration V5: Structured location hierarchy, RETRY notification status
USE booking_portal;

ALTER TABLE listings ADD COLUMN location_state VARCHAR(50) DEFAULT NULL;
ALTER TABLE listings ADD COLUMN location_city VARCHAR(100) DEFAULT NULL;
ALTER TABLE listings ADD COLUMN location_neighborhood VARCHAR(100) DEFAULT NULL;

INSERT INTO schema_version (version, description) VALUES (5, 'Structured location hierarchy for listings');
