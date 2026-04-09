-- Baseline seed data
USE booking_portal;

-- Roles
INSERT INTO roles (id, name, description) VALUES
(1, 'CUSTOMER',      'Can create and manage own bookings'),
(2, 'PHOTOGRAPHER',  'Can view assigned bookings and upload attachments'),
(3, 'ADMINISTRATOR', 'Full system access');

-- Users (all passwords are "password123" hashed with BCrypt)
-- BCrypt hash generated via: BCryptPasswordEncoder().encode("password123")
INSERT INTO users (id, username, email, password_hash, full_name, phone, role_id) VALUES
(1, 'admin',    'admin@bookingportal.com',     '$PLACEHOLDER$', 'System Administrator', '+1-555-0100', 3),
(2, 'jphoto',   'john.photo@bookingportal.com','$PLACEHOLDER$', 'John Photographer',    '+1-555-0201', 2),
(3, 'sphoto',   'sarah.photo@bookingportal.com','$PLACEHOLDER$','Sarah Photographer',   '+1-555-0202', 2),
(4, 'customer1','alice@example.com',            '$PLACEHOLDER$', 'Alice Johnson',        '+1-555-0301', 1),
(5, 'customer2','bob@example.com',              '$PLACEHOLDER$', 'Bob Williams',         '+1-555-0302', 1);

-- Services
INSERT INTO services (id, name, description, price, duration_minutes) VALUES
(1, 'Portrait Session',   'Professional portrait photography session', 150.00, 60),
(2, 'Wedding Photography','Full wedding day coverage',                 2500.00, 480),
(3, 'Event Coverage',     'Corporate or social event photography',     800.00, 240),
(4, 'Product Photography','Product shots for e-commerce',              300.00, 120),
(5, 'Family Session',     'Family portrait photography session',       200.00, 90);

-- Sample bookings
INSERT INTO bookings (id, customer_id, photographer_id, service_id, booking_date, start_time, end_time, status, location, notes, total_price) VALUES
(1, 4, 2, 1, '2026-05-15', '10:00:00', '11:00:00', 'CONFIRMED', '123 Main St, Studio A', 'Headshots for LinkedIn profile', 150.00),
(2, 5, 3, 3, '2026-05-20', '14:00:00', '18:00:00', 'PENDING',   '456 Oak Ave, Grand Hall', 'Company annual gala', 800.00),
(3, 4, NULL, 5, '2026-06-01', '09:00:00', '10:30:00', 'PENDING', 'Central Park, East Meadow', 'Family reunion photos', 200.00);
