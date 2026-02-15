-- Add distribution_scope to projects table
-- Determines which license obligations apply based on how the project is distributed
-- Values: INTERNAL, BINARY, SOURCE, SAAS, EMBEDDED
ALTER TABLE projects ADD COLUMN distribution_scope VARCHAR(50) DEFAULT 'BINARY';
