ALTER TABLE users
    ADD COLUMN phone_number VARCHAR(40),
    ADD COLUMN personal_email VARCHAR(255),
    ADD COLUMN address_line1 VARCHAR(200),
    ADD COLUMN address_line2 VARCHAR(200),
    ADD COLUMN city VARCHAR(100),
    ADD COLUMN state_province VARCHAR(100),
    ADD COLUMN postal_code VARCHAR(20),
    ADD COLUMN country VARCHAR(100),
    ADD COLUMN blood_group VARCHAR(3),
    ADD COLUMN emergency_contact_name VARCHAR(200),
    ADD COLUMN emergency_contact_relationship VARCHAR(100),
    ADD COLUMN emergency_contact_phone VARCHAR(40),
    ADD COLUMN emergency_contact_email VARCHAR(255) ;
