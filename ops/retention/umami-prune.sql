-- Only the existing Metin2 Bazar analytics website; never marketplace tables.
-- Execute with psql ON_ERROR_STOP=1. Scheduled daily in UTC.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';
SELECT pg_advisory_xact_lock(6754, 20261001);
DO $$
BEGIN
    IF (SELECT count(*) FROM website WHERE website_id = '6754c5f8-9c5d-4b21-babb-5fa96b970c5f') <> 1 THEN
        RAISE EXCEPTION 'Expected exactly one configured analytics website';
    END IF;
END $$;
DELETE FROM event_data
 WHERE website_id = '6754c5f8-9c5d-4b21-babb-5fa96b970c5f'
   AND (created_at < now() - interval '12 months' OR website_event_id IN
       (SELECT event_id FROM website_event
         WHERE website_id = '6754c5f8-9c5d-4b21-babb-5fa96b970c5f'
           AND created_at < now() - interval '12 months'));
DELETE FROM revenue WHERE website_id = '6754c5f8-9c5d-4b21-babb-5fa96b970c5f' AND created_at < now() - interval '12 months';
DELETE FROM session_replay_saved WHERE website_id = '6754c5f8-9c5d-4b21-babb-5fa96b970c5f' AND created_at < now() - interval '12 months';
DELETE FROM session_replay WHERE website_id = '6754c5f8-9c5d-4b21-babb-5fa96b970c5f' AND created_at < now() - interval '12 months';
DELETE FROM heatmap_event WHERE website_id = '6754c5f8-9c5d-4b21-babb-5fa96b970c5f' AND created_at < now() - interval '12 months';
DELETE FROM session_data WHERE website_id = '6754c5f8-9c5d-4b21-babb-5fa96b970c5f' AND created_at < now() - interval '12 months';
DELETE FROM session_link WHERE website_id = '6754c5f8-9c5d-4b21-babb-5fa96b970c5f' AND created_at < now() - interval '12 months';
DELETE FROM website_event WHERE website_id = '6754c5f8-9c5d-4b21-babb-5fa96b970c5f' AND created_at < now() - interval '12 months';
-- Session metadata remains only while retained activity still references it.
DELETE FROM session s
 WHERE s.website_id = '6754c5f8-9c5d-4b21-babb-5fa96b970c5f'
   AND s.created_at < now() - interval '12 months'
   AND NOT EXISTS (SELECT 1 FROM website_event e WHERE e.website_id = s.website_id AND e.session_id = s.session_id)
   AND NOT EXISTS (SELECT 1 FROM session_replay r WHERE r.website_id = s.website_id AND r.session_id = s.session_id)
   AND NOT EXISTS (SELECT 1 FROM heatmap_event h WHERE h.website_id = s.website_id AND h.session_id = s.session_id)
   AND NOT EXISTS (SELECT 1 FROM revenue r WHERE r.website_id = s.website_id AND r.session_id = s.session_id);
COMMIT;
