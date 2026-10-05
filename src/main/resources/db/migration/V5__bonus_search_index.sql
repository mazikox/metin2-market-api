-- Supports type/minimum EXISTS filters without joining and duplicating offer rows.
CREATE INDEX ix_listing_attribute_bonus_filter
    ON shop_listing_attribute (attr_type, attr_value, listing_id);
