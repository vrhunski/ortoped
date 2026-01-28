-- Add SPDX support to scans
-- Version: 4
-- Description: Add enable_spdx column to scans table for SPDX license suggestions

ALTER TABLE scans
    ADD COLUMN enable_spdx BOOLEAN DEFAULT false;

CREATE INDEX idx_scans_enable_spdx ON scans(enable_spdx);