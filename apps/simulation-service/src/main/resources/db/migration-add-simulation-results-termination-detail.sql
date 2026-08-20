-- 2026-08-14: additive migration for existing simulation databases.
-- Adds simulation_results.termination_detail used by the engine's STALLED
-- termination detail (global/partial stall classification).
-- New installs get this column from db/schema.sql; existing databases must
-- apply this file once before deploying the new engine.
ALTER TABLE simulation_results
    ADD COLUMN termination_detail JSON NULL AFTER frame_interval_seconds;
