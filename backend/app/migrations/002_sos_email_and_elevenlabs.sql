-- Migration 002: SOS Email Alerts, Emergency Contacts enhancements, and Live Location Shares
-- Non-destructive schema additions for Raksha360.
-- Compatible with PostgreSQL 14+ / Supabase and SQLite.

ALTER TABLE emergency_contacts ADD COLUMN IF NOT EXISTS phone TEXT NOT NULL DEFAULT '';
ALTER TABLE emergency_contacts ADD COLUMN IF NOT EXISTS is_verified INTEGER NOT NULL DEFAULT 0;
ALTER TABLE emergency_contacts ADD COLUMN IF NOT EXISTS verification_code TEXT NOT NULL DEFAULT '';
ALTER TABLE emergency_contacts ADD COLUMN IF NOT EXISTS can_share_location INTEGER NOT NULL DEFAULT 1;
ALTER TABLE emergency_contacts ADD COLUMN IF NOT EXISTS user_id TEXT NOT NULL DEFAULT '';

CREATE TABLE IF NOT EXISTS live_location_shares (
    token_hash TEXT PRIMARY KEY,
    event_id TEXT NOT NULL REFERENCES incidents(event_id),
    user_id TEXT DEFAULT '',
    device_id TEXT DEFAULT '',
    expires_at BIGINT NOT NULL,
    is_revoked INTEGER NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_live_location_shares_event ON live_location_shares(event_id);

CREATE TABLE IF NOT EXISTS sent_sos_emails (
    id TEXT PRIMARY KEY,
    event_id TEXT NOT NULL REFERENCES incidents(event_id),
    recipient_email TEXT NOT NULL,
    recipient_name TEXT NOT NULL,
    delivery_mode TEXT NOT NULL,
    delivery_status TEXT NOT NULL,
    attempted_at BIGINT NOT NULL,
    sent_at BIGINT,
    live_track_url TEXT,
    error_message TEXT,
    UNIQUE(event_id, recipient_email)
);
CREATE INDEX IF NOT EXISTS idx_sent_sos_emails_event ON sent_sos_emails(event_id);
