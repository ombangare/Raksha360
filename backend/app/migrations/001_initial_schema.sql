-- Raksha360 Initial PostgreSQL Schema Migration
-- Designed for Supabase / PostgreSQL 15+
-- All timestamps use BIGINT for millisecond epoch precision

CREATE TABLE IF NOT EXISTS incidents (
    event_id TEXT PRIMARY KEY,
    trigger_type TEXT NOT NULL,
    phrase_detected TEXT NOT NULL DEFAULT '',
    speaker_confidence REAL NOT NULL DEFAULT 0,
    reported_at BIGINT NOT NULL,
    status TEXT NOT NULL DEFAULT 'New',
    severity TEXT NOT NULL DEFAULT 'High',
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    accuracy_meters REAL,
    location_description TEXT NOT NULL DEFAULT 'Location unavailable',
    device_session_id TEXT,
    is_demo INTEGER NOT NULL DEFAULT 0,
    source TEXT NOT NULL DEFAULT 'ANDROID_APP',
    updated_at BIGINT NOT NULL,
    assignment_station TEXT,
    assigned_officer TEXT,
    notes TEXT NOT NULL DEFAULT '',
    details_json TEXT NOT NULL DEFAULT '{}',
    last_location_at BIGINT,
    last_latitude DOUBLE PRECISION,
    last_longitude DOUBLE PRECISION
);
CREATE INDEX IF NOT EXISTS idx_incidents_reported ON incidents(reported_at DESC);
CREATE INDEX IF NOT EXISTS idx_incidents_status ON incidents(status);

CREATE TABLE IF NOT EXISTS location_updates (
    id TEXT PRIMARY KEY,
    event_id TEXT NOT NULL REFERENCES incidents(event_id) ON DELETE CASCADE,
    timestamp BIGINT NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    accuracy_meters REAL,
    speed_kmph REAL,
    battery_pct INTEGER,
    created_at BIGINT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_locations_event_time ON location_updates(event_id, timestamp DESC);

CREATE TABLE IF NOT EXISTS reports (
    report_id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    category TEXT NOT NULL,
    description TEXT NOT NULL,
    location_description TEXT NOT NULL DEFAULT 'Location unavailable',
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    reported_at BIGINT NOT NULL,
    status TEXT NOT NULL DEFAULT 'New',
    priority TEXT NOT NULL DEFAULT 'Medium',
    source TEXT NOT NULL DEFAULT 'ANDROID_APP',
    source_event_id TEXT,
    evidence_sha256 TEXT,
    is_demo INTEGER NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_reports_reported ON reports(reported_at DESC);

CREATE TABLE IF NOT EXISTS trusted_contact_alerts (
    alert_id TEXT PRIMARY KEY,
    event_id TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    contact_count INTEGER NOT NULL,
    delivery_status TEXT NOT NULL,
    channels_json TEXT NOT NULL,
    details_json TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS dispatches (
    dispatch_id TEXT PRIMARY KEY,
    event_id TEXT NOT NULL REFERENCES incidents(event_id),
    requested_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    status TEXT NOT NULL DEFAULT 'REQUESTED',
    station TEXT,
    assigned_officer TEXT,
    unit_label TEXT,
    channel TEXT NOT NULL DEFAULT 'INTERNAL_CONTROL_ROOM',
    notes TEXT NOT NULL DEFAULT '',
    is_demo INTEGER NOT NULL DEFAULT 0,
    external_gateway_status TEXT NOT NULL DEFAULT 'NOT_CONFIGURED'
);
CREATE INDEX IF NOT EXISTS idx_dispatches_requested ON dispatches(requested_at DESC);

CREATE TABLE IF NOT EXISTS audit_log (
    id TEXT PRIMARY KEY,
    created_at BIGINT NOT NULL,
    actor TEXT NOT NULL,
    action TEXT NOT NULL,
    target_id TEXT,
    message TEXT NOT NULL,
    is_demo INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_audit_log_created ON audit_log(created_at DESC);

CREATE TABLE IF NOT EXISTS stations (
    station_id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    zone TEXT NOT NULL,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    status TEXT NOT NULL DEFAULT 'UNVERIFIED',
    is_demo INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS police_station_directory (
    station_id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    name_mr TEXT NOT NULL DEFAULT '',
    phone TEXT,
    email TEXT,
    address TEXT NOT NULL DEFAULT '',
    jurisdiction_label TEXT NOT NULL DEFAULT '',
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    coordinates_verified INTEGER NOT NULL DEFAULT 0,
    coordinate_source TEXT,
    coordinate_checked_at TEXT,
    coordinate_status TEXT NOT NULL DEFAULT 'OFFICIAL_ADDRESS_ONLY',
    osm_type TEXT,
    osm_id TEXT,
    source_url TEXT NOT NULL,
    source_page_last_updated TEXT,
    retrieved_on TEXT NOT NULL,
    data_class TEXT NOT NULL DEFAULT 'OFFICIAL_PUBLIC_DIRECTORY',
    updated_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_police_station_directory_name ON police_station_directory(name);

CREATE TABLE IF NOT EXISTS device_status (
    device_id TEXT PRIMARY KEY,
    last_seen_at BIGINT NOT NULL,
    last_latitude DOUBLE PRECISION,
    last_longitude DOUBLE PRECISION,
    last_accuracy_meters REAL,
    last_location_at BIGINT,
    last_battery_pct INTEGER,
    monitoring_enabled INTEGER NOT NULL DEFAULT 0,
    offline_location_email_consent INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'DISARMED',
    offline_detected_at BIGINT,
    offline_incident_id TEXT,
    offline_email_last_attempt_at BIGINT,
    offline_email_sent_at BIGINT,
    offline_email_delivery_status TEXT NOT NULL DEFAULT 'NOT_ATTEMPTED',
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_device_status_monitoring ON device_status(monitoring_enabled, last_seen_at);

CREATE TABLE IF NOT EXISTS emergency_contacts (
    device_id TEXT NOT NULL,
    email TEXT NOT NULL,
    name TEXT NOT NULL,
    relationship TEXT NOT NULL DEFAULT '',
    notify_by_email INTEGER NOT NULL DEFAULT 0,
    is_active INTEGER NOT NULL DEFAULT 1,
    updated_at BIGINT NOT NULL,
    PRIMARY KEY (device_id, email)
);

CREATE TABLE IF NOT EXISTS offline_email_deliveries (
    device_id TEXT NOT NULL,
    incident_id TEXT NOT NULL,
    email TEXT NOT NULL,
    status TEXT NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_attempt_at BIGINT,
    sent_at BIGINT,
    last_error TEXT,
    PRIMARY KEY (device_id, incident_id, email)
);
CREATE INDEX IF NOT EXISTS idx_offline_email_delivery_episode
    ON offline_email_deliveries(device_id, incident_id, status);
