-- Baseline seed data
USE booking_portal;

-- Roles
INSERT INTO roles (id, name, description) VALUES
(1, 'CUSTOMER',          'Can create and manage own orders'),
(2, 'PHOTOGRAPHER',      'Can manage listings and fulfill orders'),
(3, 'ADMINISTRATOR',     'Full system access'),
(4, 'SERVICE_PROVIDER',  'General service provider — can manage listings and fulfill orders');

-- Users (all passwords are "password123" hashed with BCrypt)
-- BCrypt hash generated via: BCryptPasswordEncoder().encode("password123")
INSERT INTO users (id, username, email, password_hash, full_name, phone, role_id) VALUES
(1, 'admin',    'admin@bookingportal.com',     '$PLACEHOLDER$', 'System Administrator', '+1-555-0100', 3),
(2, 'jphoto',   'john.photo@bookingportal.com','$PLACEHOLDER$', 'John Photographer',    '+1-555-0201', 2),
(3, 'sphoto',   'sarah.photo@bookingportal.com','$PLACEHOLDER$','Sarah Photographer',   '+1-555-0202', 2),
(4, 'customer1','alice@example.com',            '$PLACEHOLDER$', 'Alice Johnson',        '+1-555-0301', 1),
(5, 'customer2','bob@example.com',              '$PLACEHOLDER$', 'Bob Williams',         '+1-555-0302', 1);

