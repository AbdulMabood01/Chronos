-- Read-only review of leave requests that predate overlap validation.
-- Resolve each pair by rejecting or correcting one request before approving either.
SELECT a.user_id,
       a.id AS first_request_id, a.status AS first_status,
       a.start_date AS first_start, a.end_date AS first_end,
       b.id AS second_request_id, b.status AS second_status,
       b.start_date AS second_start, b.end_date AS second_end
FROM vacation_requests a
JOIN vacation_requests b ON b.user_id = a.user_id AND b.id > a.id
    AND a.start_date <= b.end_date AND b.start_date <= a.end_date
WHERE a.status IN ('SUBMITTED', 'APPROVED', 'LOCKED')
  AND b.status IN ('SUBMITTED', 'APPROVED', 'LOCKED')
ORDER BY a.user_id, a.start_date, a.id, b.id;
