DO $$
DECLARE t text; n bigint;
BEGIN
  FOREACH t IN ARRAY ARRAY['website_event','event_data','revenue','session_replay_saved','session_replay','heatmap_event','session_data','session_link'] LOOP
    EXECUTE format('SELECT count(*) FROM %I', t) INTO n;
    IF n <> 2 THEN RAISE EXCEPTION 'Table % retained % rows, expected 2', t, n; END IF;
    EXECUTE format('SELECT count(*) FROM %I WHERE website_id=$1', t) INTO n
      USING '00000000-0000-0000-0000-000000000099'::uuid;
    IF n <> 1 THEN RAISE EXCEPTION 'Other website altered in %', t; END IF;
  END LOOP;
  IF (SELECT count(*) FROM session) <> 3 THEN RAISE EXCEPTION 'Session retention failed'; END IF;
  IF EXISTS (SELECT 1 FROM session WHERE session_id='00000000-0000-0000-0000-000000000001') THEN
    RAISE EXCEPTION 'Expired inactive session retained';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM session WHERE session_id='00000000-0000-0000-0000-000000000002') THEN
    RAISE EXCEPTION 'Session with current activity deleted';
  END IF;
END $$;
SELECT 'retention tests passed: expired data, related data, current activity, other website isolation' AS result;
