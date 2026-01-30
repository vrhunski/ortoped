-- V5__ort_scan_cache.sql
-- PostgreSQL cache tables for ORT scan results

-- Cache for ORT analyzer results (per-package)
CREATE TABLE ort_package_cache (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Package identifier (Maven:group:artifact:version)
    package_id VARCHAR(500) NOT NULL,
    package_type VARCHAR(50) NOT NULL,  -- Maven, NPM, PyPI, etc.

    -- Version info for cache invalidation
    ort_version VARCHAR(50) NOT NULL,
    analyzer_version VARCHAR(50),

    -- Cached data
    declared_licenses JSONB,
    concluded_license VARCHAR(255),
    homepage_url VARCHAR(1000),
    vcs_url VARCHAR(1000),
    description TEXT,

    -- Provenance for deduplication
    source_artifact_hash VARCHAR(128),
    vcs_revision VARCHAR(128),

    -- Metadata
    created_at TIMESTAMP DEFAULT NOW(),
    last_accessed_at TIMESTAMP DEFAULT NOW(),
    access_count INTEGER DEFAULT 1,

    -- Unique constraint for deduplication
    UNIQUE(package_id, ort_version)
);

CREATE INDEX idx_ort_package_cache_type ON ort_package_cache(package_type);
CREATE INDEX idx_ort_package_cache_accessed ON ort_package_cache(last_accessed_at);

-- Cache for full scan results (per-project)
CREATE TABLE ort_scan_result_cache (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Project identification
    project_url VARCHAR(1000),
    project_revision VARCHAR(128),
    project_path VARCHAR(500),

    -- Cache key components
    ort_version VARCHAR(50) NOT NULL,
    config_hash VARCHAR(64) NOT NULL,  -- Hash of analyzer config

    -- Full ORT result (compressed)
    ort_result_json BYTEA,  -- GZIP compressed
    result_size_bytes INTEGER,

    -- Statistics
    package_count INTEGER,
    issue_count INTEGER,

    -- Timestamps
    created_at TIMESTAMP DEFAULT NOW(),
    expires_at TIMESTAMP,  -- Optional TTL

    UNIQUE(project_url, project_revision, config_hash, ort_version)
);

CREATE INDEX idx_ort_scan_cache_url ON ort_scan_result_cache(project_url);
CREATE INDEX idx_ort_scan_cache_expires ON ort_scan_result_cache(expires_at);

-- License resolution cache (AI + SPDX results)
CREATE TABLE license_resolution_cache (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Input
    package_id VARCHAR(500) NOT NULL,
    declared_license_raw TEXT,

    -- Resolution result
    resolved_spdx_id VARCHAR(255),
    resolution_source VARCHAR(50),  -- AI, SPDX_MATCH, SCANNER, MANUAL
    confidence VARCHAR(20),  -- HIGH, MEDIUM, LOW
    reasoning TEXT,

    -- Timestamps
    created_at TIMESTAMP DEFAULT NOW(),

    UNIQUE(package_id, declared_license_raw)
);

CREATE INDEX idx_license_cache_spdx ON license_resolution_cache(resolved_spdx_id);
CREATE INDEX idx_license_cache_source ON license_resolution_cache(resolution_source);
