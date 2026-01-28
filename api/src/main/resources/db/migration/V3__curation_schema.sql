-- V3: Curation System Schema
-- Description: Create tables for curation workflow, sessions, incremental tracking, and templates

-- ============================================================================
-- CURATION SESSIONS TABLE
-- Tracks overall curation progress for a scan
-- ============================================================================
CREATE TABLE curation_sessions (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    scan_id             UUID NOT NULL REFERENCES scans(id) ON DELETE CASCADE,
    status              VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',

    -- Statistics (denormalized for quick access)
    total_items         INTEGER NOT NULL DEFAULT 0,
    pending_items       INTEGER NOT NULL DEFAULT 0,
    accepted_items      INTEGER NOT NULL DEFAULT 0,
    rejected_items      INTEGER NOT NULL DEFAULT 0,
    modified_items      INTEGER NOT NULL DEFAULT 0,

    -- Approval workflow
    approved_by         VARCHAR(100),
    approved_at         TIMESTAMP WITH TIME ZONE,
    approval_comment    TEXT,

    -- Timestamps
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT unique_session_per_scan UNIQUE (scan_id)
);

CREATE INDEX idx_curation_sessions_scan_id ON curation_sessions(scan_id);
CREATE INDEX idx_curation_sessions_status ON curation_sessions(status);

COMMENT ON TABLE curation_sessions IS 'Tracks curation session progress and approval for each scan';

-- ============================================================================
-- CURATIONS TABLE
-- Individual curation decisions for each dependency
-- ============================================================================
CREATE TABLE curations (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id              UUID NOT NULL REFERENCES curation_sessions(id) ON DELETE CASCADE,
    scan_id                 UUID NOT NULL REFERENCES scans(id) ON DELETE CASCADE,
    dependency_id           VARCHAR(255) NOT NULL,
    dependency_name         VARCHAR(255) NOT NULL,
    dependency_version      VARCHAR(100) NOT NULL,
    dependency_scope        VARCHAR(50),

    -- Original license state
    original_license        VARCHAR(255),
    declared_licenses       TEXT,  -- JSON array
    detected_licenses       TEXT,  -- JSON array

    -- AI suggestion (snapshot at curation time)
    ai_suggested_license    VARCHAR(255),
    ai_confidence           VARCHAR(20),
    ai_reasoning            TEXT,
    ai_alternatives         TEXT,  -- JSON array

    -- Curation decision
    status                  VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    curated_license         VARCHAR(255),
    curator_comment         TEXT,
    curator_id              VARCHAR(100),

    -- Priority scoring (Phase 9 feature)
    priority_level          VARCHAR(20),
    priority_score          DECIMAL(5, 4),
    priority_factors        TEXT,  -- JSON array

    -- SPDX validation (Phase 9 feature)
    spdx_validated          BOOLEAN DEFAULT FALSE,
    spdx_license_data       TEXT,  -- JSON object

    -- Timestamps
    created_at              TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    curated_at              TIMESTAMP WITH TIME ZONE,

    CONSTRAINT unique_curation_per_dependency UNIQUE (session_id, dependency_id)
);

CREATE INDEX idx_curations_session_id ON curations(session_id);
CREATE INDEX idx_curations_scan_id ON curations(scan_id);
CREATE INDEX idx_curations_status ON curations(status);
CREATE INDEX idx_curations_priority ON curations(priority_level, priority_score DESC);
CREATE INDEX idx_curations_dependency_name ON curations(dependency_name);

COMMENT ON TABLE curations IS 'Individual curation decisions for dependencies within a curation session';

-- ============================================================================
-- CURATED SCANS TABLE
-- For incremental curation - tracks which scans have been fully curated
-- ============================================================================
CREATE TABLE curated_scans (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    scan_id             UUID NOT NULL REFERENCES scans(id) ON DELETE CASCADE,
    previous_scan_id    UUID REFERENCES scans(id) ON DELETE SET NULL,
    session_id          UUID REFERENCES curation_sessions(id) ON DELETE SET NULL,

    -- Curator info
    curator_id          VARCHAR(100),

    -- Dependency snapshot for diff calculation
    dependency_hash     VARCHAR(64),  -- SHA-256 of sorted dependency list
    dependency_count    INTEGER NOT NULL DEFAULT 0,

    -- Timestamps
    curated_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT unique_curated_scan UNIQUE (scan_id)
);

CREATE INDEX idx_curated_scans_scan_id ON curated_scans(scan_id);
CREATE INDEX idx_curated_scans_previous ON curated_scans(previous_scan_id);
CREATE INDEX idx_curated_scans_curated_at ON curated_scans(curated_at DESC);

COMMENT ON TABLE curated_scans IS 'Tracks completed curation sessions for incremental curation support';

-- ============================================================================
-- CURATION TEMPLATES TABLE
-- Reusable curation patterns for automated decisions
-- ============================================================================
CREATE TABLE curation_templates (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name                VARCHAR(255) NOT NULL,
    description         TEXT,

    -- Template rules (JSON)
    conditions          TEXT NOT NULL,  -- JSON array of conditions
    actions             TEXT NOT NULL,  -- JSON array of actions

    -- Metadata
    created_by          VARCHAR(100),
    is_global           BOOLEAN DEFAULT FALSE,
    is_active           BOOLEAN DEFAULT TRUE,
    usage_count         INTEGER DEFAULT 0,

    -- Timestamps
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_curation_templates_name ON curation_templates(name);
CREATE INDEX idx_curation_templates_active ON curation_templates(is_active);
CREATE INDEX idx_curation_templates_global ON curation_templates(is_global);

COMMENT ON TABLE curation_templates IS 'Reusable templates for automated curation decisions';

-- ============================================================================
-- INSERT DEFAULT TEMPLATES
-- ============================================================================
INSERT INTO curation_templates (name, description, conditions, actions, is_global, created_by) VALUES
(
    'Accept High-Confidence Permissive',
    'Auto-accept MIT, Apache-2.0, BSD licenses when AI confidence is HIGH',
    '[
        {"type": "AI_CONFIDENCE_EQUALS", "value": "HIGH"},
        {"type": "LICENSE_IN", "value": ["MIT", "Apache-2.0", "BSD-2-Clause", "BSD-3-Clause", "ISC"]}
    ]',
    '[
        {"type": "ACCEPT_AI"},
        {"type": "ADD_COMMENT", "value": "Auto-accepted by template: High-confidence permissive license"}
    ]',
    true,
    'system'
),
(
    'Flag Copyleft for Review',
    'Flag GPL and AGPL licenses as requiring manual review regardless of confidence',
    '[
        {"type": "LICENSE_IN", "value": ["GPL-2.0-only", "GPL-2.0-or-later", "GPL-3.0-only", "GPL-3.0-or-later", "AGPL-3.0-only", "AGPL-3.0-or-later"]}
    ]',
    '[
        {"type": "SET_PRIORITY", "value": "CRITICAL"},
        {"type": "ADD_COMMENT", "value": "Copyleft license requires legal review"}
    ]',
    true,
    'system'
),
(
    'Reject Low-Confidence Unknown',
    'Reject AI suggestions with LOW confidence for unknown licenses',
    '[
        {"type": "AI_CONFIDENCE_EQUALS", "value": "LOW"},
        {"type": "ORIGINAL_LICENSE_IN", "value": ["NOASSERTION", "Unknown", null]}
    ]',
    '[
        {"type": "REJECT"},
        {"type": "ADD_COMMENT", "value": "Low confidence suggestion rejected - requires manual verification"}
    ]',
    true,
    'system'
);

-- ============================================================================
-- TRIGGER: Update curation_sessions.updated_at
-- ============================================================================
CREATE OR REPLACE FUNCTION update_curation_session_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_curation_sessions_updated_at
    BEFORE UPDATE ON curation_sessions
    FOR EACH ROW
    EXECUTE FUNCTION update_curation_session_timestamp();

-- ============================================================================
-- TRIGGER: Update curation_templates.updated_at
-- ============================================================================
CREATE TRIGGER trigger_curation_templates_updated_at
    BEFORE UPDATE ON curation_templates
    FOR EACH ROW
    EXECUTE FUNCTION update_curation_session_timestamp();
