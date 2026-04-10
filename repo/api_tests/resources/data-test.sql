INSERT INTO roles (id, name, description) VALUES (1, 'CUSTOMER', 'Customer'), (2, 'PHOTOGRAPHER', 'Photographer'), (3, 'ADMINISTRATOR', 'Admin'), (4, 'SERVICE_PROVIDER', 'General service provider');
-- password is 'password123' BCrypt: $2a$10$dXJ3SW6G7P50lGmMQgel2u... (will be replaced by DataInitializer)
INSERT INTO users (id, username, email, password_hash, full_name, phone, role_id, enabled, points_balance) VALUES
(1, 'admin', 'admin@test.com', '$PLACEHOLDER$', 'Admin User', '+1-555-0001', 3, TRUE, 0),
(2, 'photo1', 'photo1@test.com', '$PLACEHOLDER$', 'Photographer One', '+1-555-0002', 2, TRUE, 0),
(3, 'photo2', 'photo2@test.com', '$PLACEHOLDER$', 'Photographer Two', '+1-555-0003', 2, TRUE, 0),
(4, 'cust1', 'cust1@test.com', '$PLACEHOLDER$', 'Customer One', '+1-555-0004', 1, TRUE, 0),
(5, 'cust2', 'cust2@test.com', '$PLACEHOLDER$', 'Customer Two', '+1-555-0005', 1, TRUE, 0);
INSERT INTO listings (id, photographer_id, title, description, category, price, duration_minutes, location, max_concurrent, active) VALUES
(1, 2, 'Studio Portrait', 'Professional portrait', 'PORTRAIT', 150.00, 60, 'Downtown', 1, TRUE),
(2, 3, 'Outdoor Family', 'Family portraits', 'FAMILY', 250.00, 90, 'City Park', 2, TRUE),
(3, 2, 'Product Shots', 'Product photography', 'PRODUCT', 300.00, 120, 'Studio B', 3, TRUE);
INSERT INTO time_slots (id, listing_id, slot_date, start_time, end_time, capacity, booked_count, version) VALUES
(1, 1, '2026-06-15', '09:00:00', '10:00:00', 1, 0, 0),
(2, 1, '2026-06-15', '11:00:00', '12:00:00', 1, 0, 0),
(3, 2, '2026-06-20', '10:00:00', '11:30:00', 2, 0, 0),
(4, 3, '2026-06-22', '09:00:00', '11:00:00', 3, 0, 0),
(5, 1, '2026-06-16', '09:00:00', '10:00:00', 1, 0, 0);
INSERT INTO addresses (id, user_id, label, street, city, state, postal_code, country, is_default) VALUES
(1, 4, 'Home', '123 Main St', 'Springfield', 'IL', '62701', 'US', TRUE),
(2, 4, 'Work', '456 Office Rd', 'Springfield', 'IL', '62702', 'US', FALSE);
INSERT INTO notification_preferences (user_id) VALUES (1), (2), (3), (4), (5);
INSERT INTO points_rules (id, name, description, points, scope, trigger_event, active) VALUES
(1, 'ORDER_PAYMENT', 'Points for payment', 10, 'INDIVIDUAL', 'ORDER_PAID', TRUE),
(2, 'ORDER_COMPLETED', 'Completion bonus', 20, 'INDIVIDUAL', 'ORDER_COMPLETED', TRUE);
