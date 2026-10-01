-- psql test using temporary copies. Shadows every table touched by pruning.
-- No production rows are changed, and all fixtures disappear on disconnect.
CREATE TEMP TABLE website AS SELECT * FROM public.website WHERE website_id = '6754c5f8-9c5d-4b21-babb-5fa96b970c5f';
CREATE TEMP TABLE website_event AS SELECT * FROM public.website_event WITH NO DATA;
CREATE TEMP TABLE event_data AS SELECT * FROM public.event_data WITH NO DATA;
CREATE TEMP TABLE revenue AS SELECT * FROM public.revenue WITH NO DATA;
CREATE TEMP TABLE session_replay_saved AS SELECT * FROM public.session_replay_saved WITH NO DATA;
CREATE TEMP TABLE session_replay AS SELECT * FROM public.session_replay WITH NO DATA;
CREATE TEMP TABLE heatmap_event AS SELECT * FROM public.heatmap_event WITH NO DATA;
CREATE TEMP TABLE session_data AS SELECT * FROM public.session_data WITH NO DATA;
CREATE TEMP TABLE session_link AS SELECT * FROM public.session_link WITH NO DATA;
CREATE TEMP TABLE session AS SELECT * FROM public.session WITH NO DATA;
SET search_path = pg_temp, public;
INSERT INTO session (website_id, session_id, created_at) VALUES
('6754c5f8-9c5d-4b21-babb-5fa96b970c5f','00000000-0000-0000-0000-000000000001',now()-interval '13 months'),
('6754c5f8-9c5d-4b21-babb-5fa96b970c5f','00000000-0000-0000-0000-000000000002',now()-interval '13 months'),
('6754c5f8-9c5d-4b21-babb-5fa96b970c5f','00000000-0000-0000-0000-000000000003',now()),
('00000000-0000-0000-0000-000000000099','00000000-0000-0000-0000-000000000099',now()-interval '13 months');
INSERT INTO website_event (website_id, event_id, session_id, created_at) VALUES
('6754c5f8-9c5d-4b21-babb-5fa96b970c5f','00000000-0000-0000-0000-000000000011','00000000-0000-0000-0000-000000000001',now()-interval '13 months'),
('6754c5f8-9c5d-4b21-babb-5fa96b970c5f','00000000-0000-0000-0000-000000000012','00000000-0000-0000-0000-000000000002',now()),
('00000000-0000-0000-0000-000000000099','00000000-0000-0000-0000-000000000099','00000000-0000-0000-0000-000000000099',now()-interval '13 months');
INSERT INTO event_data (website_id, website_event_id, created_at) VALUES
('6754c5f8-9c5d-4b21-babb-5fa96b970c5f','00000000-0000-0000-0000-000000000011',now()),
('6754c5f8-9c5d-4b21-babb-5fa96b970c5f','00000000-0000-0000-0000-000000000012',now()),
('00000000-0000-0000-0000-000000000099','00000000-0000-0000-0000-000000000099',now()-interval '13 months');
DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['revenue','session_replay_saved','session_replay','heatmap_event','session_data','session_link'] LOOP
    EXECUTE format('INSERT INTO %I (website_id, created_at) VALUES ($1, now()-interval ''13 months''), ($1, now()), ($2, now()-interval ''13 months'')', t)
      USING '6754c5f8-9c5d-4b21-babb-5fa96b970c5f'::uuid, '00000000-0000-0000-0000-000000000099'::uuid;
  END LOOP;
END $$;
-- The test runner appends umami-prune.sql, then test-umami-assert.sql.
