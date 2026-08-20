ALTER TABLE simulation_options
    ADD COLUMN initial_response_time_mean DECIMAL(8, 4) NOT NULL DEFAULT 0.0000 AFTER reaction_time,
    ADD COLUMN initial_response_time_std_dev DECIMAL(8, 4) NOT NULL DEFAULT 0.0000 AFTER initial_response_time_mean;
