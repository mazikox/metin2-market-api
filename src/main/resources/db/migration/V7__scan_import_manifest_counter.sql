-- Maintain completion in the import transaction instead of recounting the
-- entire observation history during each public query.
ALTER TABLE scan_run ADD COLUMN imported_observations BIGINT NOT NULL DEFAULT 0 CHECK (imported_observations >= 0);
UPDATE scan_run r SET imported_observations = (SELECT count(*) FROM shop_observation o WHERE o.scan_run_id = r.id);
CREATE FUNCTION market_observation_count() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP <> 'INSERT' AND OLD.scan_run_id IS NOT NULL THEN
        UPDATE scan_run SET imported_observations = imported_observations - 1 WHERE id = OLD.scan_run_id;
    END IF;
    IF TG_OP <> 'DELETE' AND NEW.scan_run_id IS NOT NULL THEN
        UPDATE scan_run SET imported_observations = imported_observations + 1 WHERE id = NEW.scan_run_id;
    END IF;
    RETURN NULL;
END;
$$;
CREATE TRIGGER maintain_scan_observation_count
AFTER INSERT OR DELETE OR UPDATE OF scan_run_id ON shop_observation
FOR EACH ROW EXECUTE FUNCTION market_observation_count();
CREATE OR REPLACE VIEW market_eligible_scan AS
SELECT r.* FROM scan_run r
WHERE r.state = 3 AND r.publishable = true
  AND (r.expected_observations IS NULL OR r.expected_observations = r.imported_observations);
CREATE INDEX ix_scan_run_ready_map ON scan_run
    (market_map_id, ended_at DESC NULLS LAST, started_at DESC, id DESC)
    WHERE state = 3 AND publishable = true
      AND (expected_observations IS NULL OR expected_observations = imported_observations);
