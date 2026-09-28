BEGIN;

DO $migration$
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
    IF to_regnamespace('pandora') IS NOT NULL THEN
        RAISE EXCEPTION 'Schema pandora already exists; inspect the database and do not rerun blindly';
    END IF;

    FOREACH table_name IN ARRAY required_tables LOOP
        IF to_regclass(format('public.%I', table_name)) IS NULL THEN
            RAISE EXCEPTION 'Required relation public.% is missing', table_name;
        END IF;
    END LOOP;

    IF EXISTS (
        SELECT 1
        FROM public.flyway_schema_history
        WHERE success IS DISTINCT FROM TRUE
           OR version IS NULL
           OR version NOT IN ('1', '2')
    ) THEN
        RAISE EXCEPTION 'Flyway history differs from the expected successful V1/V2 state';
    END IF;

    SELECT count(*)
      INTO successful_versions
      FROM public.flyway_schema_history
     WHERE success = TRUE
       AND version IN ('1', '2');

    IF successful_versions <> 2 THEN
        RAISE EXCEPTION 'Expected exactly successful Flyway V1 and V2; found % rows', successful_versions;
    END IF;
END
$migration$;

CREATE SCHEMA pandora AUTHORIZATION metin_market;

ALTER TABLE public.synchronization_batch SET SCHEMA pandora;
ALTER TABLE public.scan_run SET SCHEMA pandora;
ALTER TABLE public.shop_observation SET SCHEMA pandora;
ALTER TABLE public.shop_listing SET SCHEMA pandora;
ALTER TABLE public.shop_listing_attribute SET SCHEMA pandora;
ALTER TABLE public.shop_listing_socket SET SCHEMA pandora;
ALTER TABLE public.flyway_schema_history SET SCHEMA pandora;

COMMIT;
