-- V2 Seed Data: Listings, TimeSlots, Sample Addresses
USE booking_portal;

-- Listings from photographers
INSERT INTO listings (id, photographer_id, title, description, category, price, duration_minutes, location, max_concurrent, active) VALUES
(1, 2, 'Professional Headshots', 'Studio headshot session for corporate profiles', 'PORTRAIT', 150.00, 60, 'Downtown Studio A', 1, true),
(2, 2, 'Wedding Full Day', 'Complete wedding day photography coverage', 'WEDDING', 2500.00, 480, 'On-location', 1, true),
(3, 3, 'Family Portrait Package', 'Outdoor family portrait session with prints', 'FAMILY', 250.00, 90, 'City Park', 2, true),
(4, 3, 'Corporate Event Coverage', 'Professional event photography', 'EVENT', 800.00, 240, 'On-location', 1, true),
(5, 2, 'Product Photography', 'E-commerce product shots, white background', 'PRODUCT', 300.00, 120, 'Studio B', 3, true);

-- Time slots for listings
INSERT INTO time_slots (id, listing_id, slot_date, start_time, end_time, capacity, booked_count, version) VALUES
(1, 1, '2026-05-15', '09:00:00', '10:00:00', 1, 0, 0),
(2, 1, '2026-05-15', '10:30:00', '11:30:00', 1, 0, 0),
(3, 1, '2026-05-16', '09:00:00', '10:00:00', 1, 0, 0),
(4, 3, '2026-05-20', '10:00:00', '11:30:00', 2, 0, 0),
(5, 3, '2026-05-21', '14:00:00', '15:30:00', 2, 0, 0),
(6, 5, '2026-05-22', '09:00:00', '11:00:00', 3, 0, 0),
(7, 4, '2026-06-01', '13:00:00', '17:00:00', 1, 0, 0),
(8, 2, '2026-06-15', '08:00:00', '16:00:00', 1, 0, 0);

-- Sample addresses
INSERT INTO addresses (id, user_id, label, street, city, state, postal_code, country, is_default) VALUES
(1, 4, 'Home', '123 Elm Street', 'Springfield', 'IL', '62701', 'US', true),
(2, 4, 'Work', '456 Corporate Blvd', 'Springfield', 'IL', '62702', 'US', false),
(3, 5, 'Home', '789 Oak Avenue', 'Portland', 'OR', '97201', 'US', true);
