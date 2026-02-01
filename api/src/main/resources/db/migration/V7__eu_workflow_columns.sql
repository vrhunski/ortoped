-- V7: EU Workflow Columns
-- Add curator and approver details to curation sessions for EU compliance reporting

-- Add curator columns
ALTER TABLE curation_sessions ADD COLUMN IF NOT EXISTS curator_id VARCHAR(100) DEFAULT 'system';
ALTER TABLE curation_sessions ADD COLUMN IF NOT EXISTS curator_name VARCHAR(255);

-- Add approver details columns
ALTER TABLE curation_sessions ADD COLUMN IF NOT EXISTS approver_name VARCHAR(255);
ALTER TABLE curation_sessions ADD COLUMN IF NOT EXISTS approver_role VARCHAR(50);

-- Create indexes for reporting queries
CREATE INDEX IF NOT EXISTS idx_curation_sessions_curator ON curation_sessions(curator_id);
CREATE INDEX IF NOT EXISTS idx_curation_sessions_approver ON curation_sessions(approved_by);

COMMENT ON COLUMN curation_sessions.curator_id IS 'ID of the user who created/curated this session';
COMMENT ON COLUMN curation_sessions.curator_name IS 'Display name of the curator';
COMMENT ON COLUMN curation_sessions.approver_name IS 'Display name of the approver';
COMMENT ON COLUMN curation_sessions.approver_role IS 'Role of the approver (LEGAL, COMPLIANCE, MANAGER, etc.)';
