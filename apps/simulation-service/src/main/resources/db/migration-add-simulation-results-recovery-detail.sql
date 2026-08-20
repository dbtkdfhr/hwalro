-- 2026-08-17: additive migration for existing simulation databases.
-- Adds simulation_results.recovery_detail used by the engine's shared-target
-- recovery summary (rollout flag: simulation.engine.shared-target-recovery-enabled).
-- New installs get this column from db/schema.sql; existing databases must
-- apply this file once before deploying the new engine.
ALTER TABLE simulation_results
    ADD COLUMN recovery_detail JSON NULL AFTER termination_detail;
