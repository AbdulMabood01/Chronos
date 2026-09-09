DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_enum e
        JOIN pg_type t ON t.oid = e.enumtypid
        WHERE t.typname = 'letter_request_type'
          AND e.enumlabel = 'IMMIGRATION'
    ) AND NOT EXISTS (
        SELECT 1
        FROM pg_enum e
        JOIN pg_type t ON t.oid = e.enumtypid
        WHERE t.typname = 'letter_request_type'
          AND e.enumlabel = 'TRAVEL'
    ) THEN
        ALTER TYPE letter_request_type RENAME VALUE 'IMMIGRATION' TO 'TRAVEL';
    END IF;
END $$;

ALTER TABLE letter_requests
    ADD COLUMN IF NOT EXISTS requested_full_name VARCHAR(200),
    ADD COLUMN IF NOT EXISTS requested_job_title VARCHAR(120),
    ADD COLUMN IF NOT EXISTS employment_start_date DATE,
    ADD COLUMN IF NOT EXISTS travel_start_date DATE,
    ADD COLUMN IF NOT EXISTS travel_end_date DATE;
