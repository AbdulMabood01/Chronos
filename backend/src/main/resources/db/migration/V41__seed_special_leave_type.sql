INSERT INTO vacation_types (name, description)
VALUES ('SPECIAL', 'Special Leave')
ON CONFLICT (name) DO UPDATE SET description = EXCLUDED.description;
