ALTER TABLE users ADD COLUMN terms_version varchar(40);
ALTER TABLE users ADD COLUMN terms_accepted_at timestamptz;
