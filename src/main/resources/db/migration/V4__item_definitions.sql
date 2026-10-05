CREATE TABLE item_definition (
    vnum INTEGER PRIMARY KEY CHECK (vnum > 0),
    item_name TEXT NOT NULL,
    item_type INTEGER NOT NULL CHECK (item_type >= 0),
    item_subtype INTEGER NOT NULL CHECK (item_subtype >= 0),
    inventory_size INTEGER NOT NULL CHECK (inventory_size BETWEEN 1 AND 3),
    anti_flags BIGINT NOT NULL CHECK (anti_flags >= 0),
    required_level INTEGER NOT NULL CHECK (required_level >= 0),
    defense INTEGER NOT NULL CHECK (defense >= 0),
    min_attack INTEGER NOT NULL CHECK (min_attack >= 0),
    max_attack INTEGER NOT NULL CHECK (max_attack >= 0),
    min_magic_attack INTEGER NOT NULL CHECK (min_magic_attack >= 0),
    max_magic_attack INTEGER NOT NULL CHECK (max_magic_attack >= 0),
    socket_count INTEGER NOT NULL CHECK (socket_count >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE item_definition_bonus (
    item_vnum INTEGER NOT NULL REFERENCES item_definition(vnum) ON DELETE CASCADE,
    slot_index INTEGER NOT NULL CHECK (slot_index BETWEEN 0 AND 2),
    bonus_type INTEGER NOT NULL CHECK (bonus_type > 0),
    bonus_value INTEGER NOT NULL,
    PRIMARY KEY (item_vnum, slot_index)
);
CREATE INDEX ix_item_definition_category_level ON item_definition (item_type, item_subtype, required_level, vnum);
CREATE INDEX ix_item_definition_bonus_filter ON item_definition_bonus (bonus_type, bonus_value, item_vnum);
