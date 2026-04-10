-- Migration V7: Explicit delivery/pickup ETA fields, SERVICE_PROVIDER role
USE booking_portal;

-- Add explicit ETA timestamps to orders instead of relying on duration-based UI text
ALTER TABLE orders ADD COLUMN delivery_eta TIMESTAMP NULL COMMENT 'Estimated delivery time for COURIER orders';
ALTER TABLE orders ADD COLUMN pickup_eta TIMESTAMP NULL COMMENT 'Estimated pickup time for PICKUP orders';

-- Add SERVICE_PROVIDER role for general (non-photographer) service providers
INSERT IGNORE INTO roles (id, name, description)
VALUES (4, 'SERVICE_PROVIDER', 'General service provider — can manage listings and fulfill orders');

INSERT INTO schema_version (version, description)
VALUES (7, 'Delivery/pickup ETA fields, SERVICE_PROVIDER role');
