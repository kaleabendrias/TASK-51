-- Migration V4: Delivery modes, notification read/archive, listing filters
USE booking_portal;

-- Add delivery mode to orders
ALTER TABLE orders ADD COLUMN delivery_mode VARCHAR(20) NOT NULL DEFAULT 'ONSITE'
    COMMENT 'ONSITE, PICKUP, or COURIER';

-- Notification read/archive/terminal state tracking
ALTER TABLE notification_queue ADD COLUMN read_at TIMESTAMP NULL;
ALTER TABLE notification_queue ADD COLUMN archived BOOLEAN DEFAULT FALSE;
ALTER TABLE notification_queue ADD COLUMN terminal BOOLEAN DEFAULT FALSE
    COMMENT 'True when no further retries will be attempted';
ALTER TABLE notification_queue MODIFY COLUMN retry_count INT NOT NULL DEFAULT 0;

-- Listing extended filters: theme, transport mode
ALTER TABLE listings ADD COLUMN theme VARCHAR(100) DEFAULT NULL;
ALTER TABLE listings ADD COLUMN transport_mode VARCHAR(50) DEFAULT NULL
    COMMENT 'WALK, DRIVE, PUBLIC_TRANSIT, etc.';
ALTER TABLE listings ADD COLUMN rating DECIMAL(3,2) DEFAULT 0.00;
ALTER TABLE listings ADD COLUMN rating_count INT DEFAULT 0;

INSERT INTO schema_version (version, description) VALUES (4, 'Delivery modes, notification tracking, listing filters');
