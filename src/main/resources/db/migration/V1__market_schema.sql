CREATE TABLE synchronization_batch (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_id TEXT NOT NULL,
    external_batch_id TEXT NOT NULL,
    payload_sha256 CHAR(64) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    imported_runs INTEGER NOT NULL,
    imported_observations INTEGER NOT NULL,
    imported_listings INTEGER NOT NULL,
    CONSTRAINT uq_synchronization_batch_source UNIQUE (source_id, external_batch_id)
);

CREATE TABLE scan_run (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_id TEXT NOT NULL,
    source_run_id TEXT NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    state INTEGER NOT NULL,
    map_id TEXT NOT NULL,
    channel INTEGER,
    total_targets INTEGER NOT NULL,
    visited_targets INTEGER NOT NULL,
    failed_targets INTEGER NOT NULL,
    CONSTRAINT uq_scan_run_source UNIQUE (source_id, source_run_id)
);

CREATE TABLE shop_observation (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_id TEXT NOT NULL,
    source_observation_id TEXT NOT NULL,
    scan_run_id BIGINT REFERENCES scan_run(id),
    shop_vid BIGINT,
    shop_title TEXT,
    owner_name TEXT,
    map_id TEXT NOT NULL,
    channel INTEGER,
    x DOUBLE PRECISION NOT NULL,
    y DOUBLE PRECISION NOT NULL,
    z DOUBLE PRECISION NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL,
    content_fingerprint TEXT NOT NULL,
    source_payload_sha256 CHAR(64) NOT NULL,
    reported_item_count INTEGER NOT NULL,
    CONSTRAINT uq_shop_observation_source UNIQUE (source_id, source_observation_id)
);

CREATE TABLE shop_listing (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    observation_id BIGINT NOT NULL REFERENCES shop_observation(id) ON DELETE CASCADE,
    source_listing_id BIGINT NOT NULL,
    slot_index INTEGER NOT NULL,
    item_vnum INTEGER NOT NULL,
    item_name TEXT NOT NULL,
    quantity INTEGER NOT NULL,
    price_raw BIGINT NOT NULL,
    unit_price BIGINT NOT NULL,
    tail_field BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_listing_source UNIQUE (observation_id, source_listing_id)
);

CREATE TABLE shop_listing_attribute (
    listing_id BIGINT NOT NULL REFERENCES shop_listing(id) ON DELETE CASCADE,
    slot_index INTEGER NOT NULL,
    attr_type INTEGER NOT NULL,
    attr_value INTEGER NOT NULL,
    PRIMARY KEY (listing_id, slot_index)
);

CREATE TABLE shop_listing_socket (
    listing_id BIGINT NOT NULL REFERENCES shop_listing(id) ON DELETE CASCADE,
    socket_index INTEGER NOT NULL,
    socket_value BIGINT NOT NULL,
    PRIMARY KEY (listing_id, socket_index)
);

CREATE INDEX ix_listing_item_name_lower ON shop_listing (lower(item_name));
CREATE INDEX ix_listing_item_vnum ON shop_listing (item_vnum);
CREATE INDEX ix_observation_observed_at ON shop_observation (observed_at DESC);
