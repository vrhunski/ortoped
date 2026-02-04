-- V8: Convert audit_logs JSONB columns to TEXT
-- Fixes type mismatch between Exposed ORM (text) and PostgreSQL (jsonb)
-- Following the same pattern as V2 migration for policies, scans, policy_evaluations

ALTER TABLE audit_logs ALTER COLUMN previous_state TYPE TEXT USING previous_state::TEXT;
ALTER TABLE audit_logs ALTER COLUMN new_state TYPE TEXT USING new_state::TEXT;
