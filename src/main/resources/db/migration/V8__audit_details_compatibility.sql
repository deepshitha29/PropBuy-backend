-- V1 created the audit explanation column as `reason`, while the application
-- records and reads the richer `details` field. Keep existing installations
-- compatible without rewriting or deleting historical audit rows.
ALTER TABLE audit_logs ADD COLUMN details VARCHAR(1000);
