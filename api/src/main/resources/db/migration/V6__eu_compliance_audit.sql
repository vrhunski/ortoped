-- V6: EU Compliance Audit Tables
-- Description: Add tables for structured justifications, audit logs, two-role approval,
-- and distribution-aware obligations per EU/German compliance requirements

-- ============================================================================
-- AUDIT LOGS TABLE
-- Immutable log of all curation actions (EU requirement for traceability)
-- ============================================================================
CREATE TABLE audit_logs (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    entity_type     VARCHAR(50) NOT NULL,   -- 'CURATION', 'SESSION', 'APPROVAL', 'JUSTIFICATION'
    entity_id       UUID NOT NULL,
    action          VARCHAR(50) NOT NULL,   -- 'CREATE', 'DECIDE', 'APPROVE', 'REJECT', 'MODIFY', 'SUBMIT'
    actor_id        VARCHAR(100) NOT NULL,
    actor_role      VARCHAR(50) NOT NULL,   -- 'CURATOR', 'APPROVER', 'SYSTEM'
    previous_state  JSONB,
    new_state       JSONB NOT NULL,
    change_summary  TEXT NOT NULL,
    ip_address      VARCHAR(45),            -- IPv6 compatible
    user_agent      TEXT,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_audit_logs_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_logs_actor ON audit_logs(actor_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at DESC);
CREATE INDEX idx_audit_logs_action ON audit_logs(action);

COMMENT ON TABLE audit_logs IS 'Immutable audit trail for EU compliance - tracks all curation decisions and approvals';

-- ============================================================================
-- CURATION JUSTIFICATIONS TABLE
-- Structured justification mandatory for non-permissive licenses (EU requirement)
-- ============================================================================
CREATE TABLE curation_justifications (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    curation_id             UUID NOT NULL REFERENCES curations(id) ON DELETE CASCADE,

    -- EU mandatory license identification fields
    spdx_id                 VARCHAR(100) NOT NULL,
    license_category        VARCHAR(50) NOT NULL,   -- 'PERMISSIVE', 'WEAK_COPYLEFT', 'STRONG_COPYLEFT', 'PROPRIETARY', 'UNKNOWN'
    concluded_license       VARCHAR(255) NOT NULL,

    -- Structured justification
    justification_type      VARCHAR(50) NOT NULL,   -- 'AI_ACCEPTED', 'MANUAL_OVERRIDE', 'EVIDENCE_BASED', 'POLICY_EXEMPTION'
    justification_text      TEXT NOT NULL,

    -- Policy linkage (which rule triggered this review)
    policy_rule_id          VARCHAR(100),
    policy_rule_name        VARCHAR(255),

    -- Evidence documentation (recommended for non-permissive)
    evidence_type           VARCHAR(50),            -- 'LICENSE_FILE', 'REPO_INSPECTION', 'VENDOR_CONFIRMATION', 'LEGAL_OPINION'
    evidence_reference      TEXT,                   -- URL, document reference, or description
    evidence_collected_at   TIMESTAMP WITH TIME ZONE,

    -- Distribution context (obligations vary by distribution type)
    distribution_scope      VARCHAR(50) NOT NULL DEFAULT 'BINARY',  -- 'INTERNAL', 'BINARY', 'SOURCE', 'SAAS', 'EMBEDDED'

    -- Curator signature
    curator_id              VARCHAR(100) NOT NULL,
    curator_name            VARCHAR(255),
    curator_email           VARCHAR(255),
    curated_at              TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    -- Integrity hash for tamper detection
    justification_hash      VARCHAR(64),            -- SHA-256 of justification content

    CONSTRAINT unique_justification_per_curation UNIQUE (curation_id)
);

CREATE INDEX idx_curation_justifications_curation ON curation_justifications(curation_id);
CREATE INDEX idx_curation_justifications_curator ON curation_justifications(curator_id);
CREATE INDEX idx_curation_justifications_category ON curation_justifications(license_category);
CREATE INDEX idx_curation_justifications_type ON curation_justifications(justification_type);

COMMENT ON TABLE curation_justifications IS 'Structured justifications for license decisions - mandatory for non-permissive licenses per EU compliance';

-- ============================================================================
-- CURATION APPROVALS TABLE
-- Two-role approval workflow (curator != approver per EU requirement)
-- ============================================================================
CREATE TABLE curation_approvals (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id          UUID NOT NULL REFERENCES curation_sessions(id) ON DELETE CASCADE,

    -- Submitter info (the curator who submits for approval)
    submitter_id        VARCHAR(100) NOT NULL,
    submitter_name      VARCHAR(255),
    submitted_at        TIMESTAMP WITH TIME ZONE NOT NULL,

    -- Approver info (must be different from curator per EU requirement)
    approver_id         VARCHAR(100),
    approver_name       VARCHAR(255),
    approver_role       VARCHAR(50),            -- 'LEGAL', 'COMPLIANCE', 'MANAGER', 'SECURITY'

    -- Decision
    decision            VARCHAR(20),            -- 'APPROVED', 'REJECTED', 'RETURNED'
    decision_comment    TEXT,
    decided_at          TIMESTAMP WITH TIME ZONE,

    -- If returned for revision
    return_reason       TEXT,
    revision_items      TEXT,                   -- JSON array of item IDs that need revision

    -- Tracking
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT unique_approval_per_session UNIQUE (session_id)
);

CREATE INDEX idx_curation_approvals_session ON curation_approvals(session_id);
CREATE INDEX idx_curation_approvals_approver ON curation_approvals(approver_id);
CREATE INDEX idx_curation_approvals_decision ON curation_approvals(decision);
CREATE INDEX idx_curation_approvals_submitted ON curation_approvals(submitted_at DESC);

COMMENT ON TABLE curation_approvals IS 'Two-role approval workflow - ensures curator and approver are different people per EU compliance';

-- ============================================================================
-- OR LICENSES TABLE
-- Track OR license resolutions (e.g., "GPL-2.0 OR MIT" -> explicit choice)
-- ============================================================================
CREATE TABLE or_license_resolutions (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    curation_id         UUID NOT NULL REFERENCES curations(id) ON DELETE CASCADE,

    -- Original OR license expression
    original_expression VARCHAR(500) NOT NULL,  -- e.g., "GPL-2.0-only OR MIT OR Apache-2.0"
    license_options     TEXT NOT NULL,          -- JSON array of available options

    -- Resolution
    chosen_license      VARCHAR(255),           -- The selected license
    choice_reason       TEXT,                   -- Why this license was chosen
    resolved_by         VARCHAR(100),
    resolved_at         TIMESTAMP WITH TIME ZONE,

    -- Status
    is_resolved         BOOLEAN DEFAULT FALSE,

    CONSTRAINT unique_or_resolution UNIQUE (curation_id)
);

CREATE INDEX idx_or_resolutions_curation ON or_license_resolutions(curation_id);
CREATE INDEX idx_or_resolutions_resolved ON or_license_resolutions(is_resolved);

COMMENT ON TABLE or_license_resolutions IS 'Explicit human choice for OR license expressions - required for EU compliance';

-- ============================================================================
-- ALTER EXISTING TABLES: Add EU compliance columns
-- ============================================================================

-- Curations table: Add justification and OR license tracking
ALTER TABLE curations ADD COLUMN IF NOT EXISTS requires_justification BOOLEAN DEFAULT TRUE;
ALTER TABLE curations ADD COLUMN IF NOT EXISTS justification_complete BOOLEAN DEFAULT FALSE;
ALTER TABLE curations ADD COLUMN IF NOT EXISTS blocking_policy_rule VARCHAR(100);
ALTER TABLE curations ADD COLUMN IF NOT EXISTS is_or_license BOOLEAN DEFAULT FALSE;
ALTER TABLE curations ADD COLUMN IF NOT EXISTS or_license_choice VARCHAR(255);
ALTER TABLE curations ADD COLUMN IF NOT EXISTS distribution_scope VARCHAR(50) DEFAULT 'BINARY';

-- Curation sessions: Add submission workflow
ALTER TABLE curation_sessions ADD COLUMN IF NOT EXISTS submitted_for_approval BOOLEAN DEFAULT FALSE;
ALTER TABLE curation_sessions ADD COLUMN IF NOT EXISTS submitted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE curation_sessions ADD COLUMN IF NOT EXISTS submitted_by VARCHAR(100);

-- Scans: Add curation requirement flags
ALTER TABLE scans ADD COLUMN IF NOT EXISTS curation_required BOOLEAN DEFAULT TRUE;
ALTER TABLE scans ADD COLUMN IF NOT EXISTS curation_complete BOOLEAN DEFAULT FALSE;

-- ============================================================================
-- CREATE INDEXES FOR NEW COLUMNS
-- ============================================================================
CREATE INDEX idx_curations_requires_justification ON curations(requires_justification) WHERE requires_justification = TRUE;
CREATE INDEX idx_curations_justification_complete ON curations(justification_complete) WHERE justification_complete = FALSE;
CREATE INDEX idx_curations_is_or_license ON curations(is_or_license) WHERE is_or_license = TRUE;
CREATE INDEX idx_curation_sessions_submitted ON curation_sessions(submitted_for_approval) WHERE submitted_for_approval = TRUE;
CREATE INDEX idx_scans_curation_required ON scans(curation_required) WHERE curation_required = TRUE;
CREATE INDEX idx_scans_curation_complete ON scans(curation_complete);

-- ============================================================================
-- FUNCTION: Calculate justification hash
-- ============================================================================
CREATE OR REPLACE FUNCTION calculate_justification_hash()
RETURNS TRIGGER AS $$
BEGIN
    NEW.justification_hash = encode(
        sha256(
            (NEW.spdx_id || '|' || NEW.concluded_license || '|' ||
             NEW.justification_type || '|' || NEW.justification_text || '|' ||
             NEW.curator_id || '|' || NEW.curated_at::text)::bytea
        ),
        'hex'
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_justification_hash
    BEFORE INSERT OR UPDATE ON curation_justifications
    FOR EACH ROW
    EXECUTE FUNCTION calculate_justification_hash();

-- ============================================================================
-- FUNCTION: Validate curator != approver
-- ============================================================================
CREATE OR REPLACE FUNCTION validate_approver_different()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.approver_id IS NOT NULL AND NEW.approver_id = NEW.submitter_id THEN
        RAISE EXCEPTION 'EU Compliance Violation: Approver must be different from submitter (four-eyes principle)';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_validate_approver
    BEFORE INSERT OR UPDATE ON curation_approvals
    FOR EACH ROW
    EXECUTE FUNCTION validate_approver_different();

-- ============================================================================
-- VIEW: Pending approvals with session details
-- ============================================================================
CREATE OR REPLACE VIEW v_pending_approvals AS
SELECT
    ca.id AS approval_id,
    ca.session_id,
    cs.scan_id,
    s.project_id,
    p.name AS project_name,
    ca.submitter_id,
    ca.submitter_name,
    ca.submitted_at,
    cs.total_items,
    cs.pending_items,
    cs.accepted_items,
    cs.rejected_items,
    cs.modified_items,
    (SELECT COUNT(*) FROM curations c WHERE c.session_id = cs.id AND c.is_or_license = TRUE AND c.or_license_choice IS NULL) AS unresolved_or_licenses,
    (SELECT COUNT(*) FROM curations c WHERE c.session_id = cs.id AND c.requires_justification = TRUE AND c.justification_complete = FALSE) AS pending_justifications
FROM curation_approvals ca
JOIN curation_sessions cs ON ca.session_id = cs.id
JOIN scans s ON cs.scan_id = s.id
JOIN projects p ON s.project_id = p.id
WHERE ca.decision IS NULL;

COMMENT ON VIEW v_pending_approvals IS 'Dashboard view for approvers - shows all sessions awaiting approval';

-- ============================================================================
-- VIEW: Audit trail for a curation session
-- ============================================================================
CREATE OR REPLACE VIEW v_session_audit_trail AS
SELECT
    al.id AS log_id,
    al.entity_type,
    al.entity_id,
    al.action,
    al.actor_id,
    al.actor_role,
    al.change_summary,
    al.created_at,
    cs.id AS session_id,
    cs.scan_id
FROM audit_logs al
LEFT JOIN curations c ON al.entity_type = 'CURATION' AND al.entity_id = c.id
LEFT JOIN curation_sessions cs ON c.session_id = cs.id OR (al.entity_type = 'SESSION' AND al.entity_id = cs.id)
ORDER BY al.created_at DESC;

COMMENT ON VIEW v_session_audit_trail IS 'Complete audit trail for curation sessions - required for EU compliance audits';
