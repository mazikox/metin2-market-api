-- Seasonal Joan has the same public market as its regular map.
CREATE FUNCTION market_map_key(raw_map TEXT) RETURNS TEXT
LANGUAGE SQL IMMUTABLE STRICT PARALLEL SAFE AS $$
    SELECT CASE WHEN raw_map = 'metin2_map_a1_summer' THEN 'metin2_map_a1' ELSE raw_map END
$$;
ALTER TABLE scan_run ADD COLUMN expected_observations INTEGER CHECK (expected_observations >= 0);
ALTER TABLE scan_run ADD COLUMN market_map_id TEXT
    GENERATED ALWAYS AS (market_map_key(map_id)) STORED;
CREATE INDEX ix_scan_run_public_map ON scan_run
    (market_map_id, ended_at DESC NULLS LAST, started_at DESC, id DESC)
    WHERE state = 3 AND publishable = true;
CREATE TABLE market_map_selection (
    map_id TEXT PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT true,
    selected_scan_id BIGINT REFERENCES scan_run(id) ON DELETE SET NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO market_map_selection (map_id)
SELECT DISTINCT market_map_id FROM scan_run
UNION SELECT 'metin2_map_a1'
UNION SELECT 'metin2_map_b1'
UNION SELECT 'metin2_map_c1';
-- Legacy imports have no manifest count; new batched imports appear atomically.
CREATE VIEW market_eligible_scan AS
SELECT r.* FROM scan_run r
WHERE r.state = 3 AND r.publishable = true
  AND (r.expected_observations IS NULL OR r.expected_observations =
       (SELECT count(*) FROM shop_observation o WHERE o.scan_run_id = r.id));
CREATE VIEW market_active_scan AS
SELECT DISTINCT ON (r.market_map_id) r.*
FROM market_eligible_scan r
LEFT JOIN market_map_selection s ON s.map_id = r.market_map_id
WHERE COALESCE(s.enabled, true)
  AND (s.selected_scan_id IS NULL OR s.selected_scan_id = r.id)
ORDER BY r.market_map_id, r.ended_at DESC NULLS LAST, r.started_at DESC, r.id DESC;
-- VIDs may collide between maps/channels; scope deduplication to both.
CREATE VIEW market_canonical_observation AS
SELECT ranked.* FROM (
    SELECT o.*, r.market_map_id,
           ROW_NUMBER() OVER (
               PARTITION BY r.market_map_id, o.channel,
                            COALESCE(o.shop_vid::text, 'obs:' || o.id::text)
               ORDER BY o.observed_at DESC, o.id DESC
           ) AS rn
    FROM market_active_scan r JOIN shop_observation o ON o.scan_run_id = r.id
    WHERE market_map_key(o.map_id) = r.market_map_id
) ranked WHERE rn = 1;
