-- Migration 003: Report Attachments & Multimodal Intake
-- Non-destructive schema addition for Raksha360.
-- Compatible with PostgreSQL 14+ / Supabase.

CREATE TABLE IF NOT EXISTS report_attachments (
    id TEXT PRIMARY KEY,
    report_id TEXT NOT NULL REFERENCES reports(report_id) ON DELETE CASCADE,
    file_name TEXT NOT NULL,
    mime_type TEXT NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    sha256_hash TEXT NOT NULL,
    storage_path TEXT NOT NULL,
    analysis_status TEXT NOT NULL,
    analysis_summary TEXT NOT NULL,
    created_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_report_attachments_report_id ON report_attachments(report_id);
