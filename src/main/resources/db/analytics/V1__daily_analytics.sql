-- No raw IP, user agent, query text or visitor-to-item relationship.
CREATE TABLE daily_totals (
    day date NOT NULL,
    server varchar(16) NOT NULL,
    uniques bigint NOT NULL DEFAULT 0,
    searches bigint NOT NULL DEFAULT 0,
    results bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (day, server)
);
CREATE TABLE daily_visitors (
    day date NOT NULL,
    server varchar(16) NOT NULL,
    visitor_hash char(64) NOT NULL,
    PRIMARY KEY (day, server, visitor_hash)
);
-- Request tokens are random per operation, never per visitor. No linking columns.
CREATE TABLE request_dedup (
    created_at timestamptz NOT NULL,
    kind varchar(8) NOT NULL,
    request_id uuid NOT NULL,
    PRIMARY KEY (kind, request_id)
);
CREATE INDEX request_dedup_created_at ON request_dedup(created_at);
CREATE TABLE daily_items (
    day date NOT NULL,
    server varchar(16) NOT NULL,
    item_name varchar(200) NOT NULL,
    searches bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (day, server, item_name)
);
