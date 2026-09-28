BEGIN;

DO $rollback$
DECLARE
    table_name TEXT;
    required_tables TEXT[] := ARRAY[
        'synchronization_batch',
        'scan_run',
        'shop_observation',
        'shop_listing',
        'shop_listing_attribute',
        'shop_listing_socket',
        'flyway_schema_history'
    ];
    successful_versions INTEGER;
BEGIN
    IF to_regnamespace('pandora') IS NULL THEN
        RAISE EXCEPTION 'Schema pandora is missing; nothing to roll back';
    END IF;

    FOREACH table_name IN ARRAY required_tables LOOP
        IF to_regclass(format('pandora.%I', table_name)) IS NULL THEN
            RAISE EXCEPTION 'Required relation pandora.% is missing', table_name;
        END IF;
        IF to_regclass(format('public.%I', table_name)) IS NOT NULL THEN
            RAISE EXCEPTION 'Relation public.% already exists; refusing to overwrite it', table_name;
        END IF;
    END LOOP;

    IF EXISTS (
        SELECT 1
        FROM pandora.flyway_schema_history
        WHERE success IS DISTINCT FROM TRUE
           OR version IS NULL
           OR version NOT IN ('1', '2')
    ) THEN
        RAISE EXCEPTION 'Pandora Flyway history differs from the expected successful V1/V2 state';
    END IF;

    SELECT count(*)
      INTO successful_versions
      FROM pandora.flyway_schema_history
     WHERE success = TRUE
       AND version IN ('1', '2');

    IF successful_versions <> 2 THEN
        RAISE EXCEPTION 'Expected exactly successful Flyway V1 and V2; found % rows', successful_versions;
    END IF;
END
$rollback$;

ALTER TABLE pandora.synchronization_batch SET SCHEMA public;
ALTER TABLE pandora.scan_run SET SCHEMA public;
ALTER TABLE pandora.shop_observation SET SCHEMA public;
ALTER TABLE pandora.shop_listing SET SCHEMA public;
ALTER TABLE pandora.shop_listing_attribute SET SCHEMA public;
ALTER TABLE pandora.shop_listing_socket SET SCHEMA public;
ALTER TABLE pandora.flyway_schema_history SET SCHEMA public;

DROP SCHEMA pandora RESTRICT;

COMMIT;
