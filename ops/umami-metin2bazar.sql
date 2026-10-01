-- Run in the Umami database during activation, not before DNS and site launch.
-- Retains the existing website ID, statistics and recorder settings.
BEGIN;
DO $$
DECLARE affected integer;
BEGIN
    UPDATE website
       SET name = 'Metin2 Bazar', domain = 'metin2bazar.pl', updated_at = now()
     WHERE website_id = '6754c5f8-9c5d-4b21-babb-5fa96b970c5f';
    GET DIAGNOSTICS affected = ROW_COUNT;
    IF affected <> 1 THEN
        RAISE EXCEPTION 'Expected exactly one existing analytics website, updated %', affected;
    END IF;
END $$;
COMMIT;
