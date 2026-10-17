-- Optimize bonus search:
-- 1. Drop the legacy index on (attr_type, attr_value, listing_id) which led PostgreSQL planner
--    to drive queries by scanning historical attributes across past scans instead of active shops.
DROP INDEX IF EXISTS ix_listing_attribute_bonus_filter;

-- 2. Create index on (listing_id, attr_type, attr_value) to allow instant verification
--    of bonus filters for candidate active shop listings.
CREATE INDEX IF NOT EXISTS ix_listing_attribute_lookup
    ON shop_listing_attribute (listing_id, attr_type, attr_value);

-- 3. Multivariate statistics so PostgreSQL understands the correlation of attr_type and attr_value.
CREATE STATISTICS IF NOT EXISTS st_listing_attribute_type_val
    (dependencies, mcv) ON attr_type, attr_value FROM shop_listing_attribute;

ANALYZE shop_listing_attribute;
