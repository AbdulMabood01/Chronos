CREATE TABLE timesheet_approval_period_events (
    id bigserial PRIMARY KEY,
    period_id bigint NOT NULL REFERENCES timesheet_approval_periods(id) ON DELETE CASCADE,
    actor_id bigint NOT NULL REFERENCES users(id),
    event varchar(30) NOT NULL,
    comment text,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX timesheet_approval_period_events_period_idx ON timesheet_approval_period_events(period_id,created_at);
