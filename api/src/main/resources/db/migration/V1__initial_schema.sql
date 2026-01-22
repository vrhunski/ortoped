-- OrtoPed API Initial Schema
-- Version: 1
-- Description: Create core tables for projects, scans, policies, and API keys

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Projects table
CREATE TABLE projects (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name            VARCHAR(255) NOT NULL,
    repository_url  VARCHAR(500),
    default_branch  VARCHAR(100) DEFAULT 'main',
    policy_id       UUID,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_projects_name ON projects(name);
CREATE INDEX idx_projects_created_at ON projects(created_at DESC);

-- Policies table (must be created before projects FK)
CREATE TABLE policies (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name            VARCHAR(255) NOT NULL,
    config          JSONB NOT NULL,
    is_default      BOOLEAN DEFAULT false,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_policies_name ON policies(name);
CREATE INDEX idx_policies_is_default ON policies(is_default);

-- Add foreign key to projects
ALTER TABLE projects
    ADD CONSTRAINT fk_projects_policy
    FOREIGN KEY (policy_id) REFERENCES policies(id) ON DELETE SET NULL;

-- Scans table
CREATE TABLE scans (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    project_id      UUID REFERENCES projects(id) ON DELETE SET NULL,
    status          VARCHAR(50) NOT NULL DEFAULT 'pending',
    enable_ai       BOOLEAN DEFAULT true,
    result          JSONB,
    summary         JSONB,
    started_at      TIMESTAMP WITH TIME ZONE,
    completed_at    TIMESTAMP WITH TIME ZONE,
    error_message   TEXT,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_scans_project_id ON scans(project_id);
CREATE INDEX idx_scans_status ON scans(status);
CREATE INDEX idx_scans_created_at ON scans(created_at DESC);

-- Policy evaluations table
CREATE TABLE policy_evaluations (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    scan_id         UUID NOT NULL REFERENCES scans(id) ON DELETE CASCADE,
    policy_id       UUID NOT NULL REFERENCES policies(id) ON DELETE CASCADE,
    passed          BOOLEAN NOT NULL,
    report          JSONB NOT NULL,
    error_count     INTEGER DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_policy_evaluations_scan_id ON policy_evaluations(scan_id);
CREATE INDEX idx_policy_evaluations_policy_id ON policy_evaluations(policy_id);
CREATE INDEX idx_policy_evaluations_passed ON policy_evaluations(passed);

-- API Keys table
CREATE TABLE api_keys (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name            VARCHAR(255) NOT NULL,
    key_hash        VARCHAR(255) NOT NULL,
    key_prefix      VARCHAR(10) NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_api_keys_key_prefix ON api_keys(key_prefix);
CREATE INDEX idx_api_keys_name ON api_keys(name);

-- Insert default policy
INSERT INTO policies (id, name, config, is_default) VALUES (
    uuid_generate_v4(),
    'Default Policy',
    '{
        "version": "1.0",
        "name": "Default OrtoPed Policy",
        "description": "Default policy that flags unknown licenses",
        "categories": {
            "permissive": {
                "description": "Permissive open source licenses",
                "licenses": ["MIT", "Apache-2.0", "BSD-2-Clause", "BSD-3-Clause", "ISC", "Unlicense", "0BSD", "CC0-1.0"]
            },
            "copyleft": {
                "description": "Strong copyleft licenses",
                "licenses": ["GPL-2.0-only", "GPL-2.0-or-later", "GPL-3.0-only", "GPL-3.0-or-later", "AGPL-3.0-only", "AGPL-3.0-or-later"]
            },
            "copyleft-limited": {
                "description": "Weak copyleft licenses with limited scope",
                "licenses": ["LGPL-2.0-only", "LGPL-2.1-only", "LGPL-3.0-only", "MPL-2.0", "EPL-1.0", "EPL-2.0"]
            },
            "unknown": {
                "description": "Unknown or unresolved licenses",
                "licenses": ["NOASSERTION", "Unknown"]
            }
        },
        "rules": [
            {
                "id": "no-unknown",
                "name": "No Unknown Licenses",
                "description": "All dependencies must have identified licenses",
                "severity": "ERROR",
                "category": "unknown",
                "action": "DENY",
                "enabled": true,
                "message": "Dependency {{dependency}} has unresolved license - manual review required"
            }
        ],
        "settings": {
            "aiSuggestions": {
                "acceptHighConfidence": true,
                "treatMediumAsWarning": true,
                "rejectLowConfidence": true
            },
            "failOn": {
                "errors": true,
                "warnings": false
            },
            "exemptions": []
        }
    }',
    true
);

-- Add comments
COMMENT ON TABLE projects IS 'Projects being scanned for license compliance';
COMMENT ON TABLE scans IS 'Scan jobs with async status tracking';
COMMENT ON TABLE policies IS 'License compliance policies';
COMMENT ON TABLE policy_evaluations IS 'Results of policy evaluations against scans';
COMMENT ON TABLE api_keys IS 'API keys for programmatic access';
