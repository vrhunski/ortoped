-- V2: Convert JSONB columns to TEXT
-- This fixes the double-encoding issue with jsonb<String> in Exposed

-- Convert policies.config from JSONB to TEXT
ALTER TABLE policies
    ALTER COLUMN config TYPE TEXT USING config::TEXT;

-- Convert scans.result from JSONB to TEXT
ALTER TABLE scans
    ALTER COLUMN result TYPE TEXT USING result::TEXT;

-- Convert scans.summary from JSONB to TEXT
ALTER TABLE scans
    ALTER COLUMN summary TYPE TEXT USING summary::TEXT;

-- Convert policy_evaluations.report from JSONB to TEXT
ALTER TABLE policy_evaluations
    ALTER COLUMN report TYPE TEXT USING report::TEXT;
