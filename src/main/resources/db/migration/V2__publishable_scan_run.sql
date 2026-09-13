ALTER TABLE scan_run ADD COLUMN publishable BOOLEAN NOT NULL DEFAULT false;

CREATE INDEX ix_scan_run_public ON scan_run (ended_at DESC, started_at DESC, id DESC)
    WHERE state = 3 AND publishable = true;

CREATE INDEX ix_shop_observation_scan_run_id ON shop_observation (scan_run_id);
