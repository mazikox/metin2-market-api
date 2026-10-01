-- Independent hourly retention, including when the API is stopped.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';
DELETE FROM analytics.daily_visitors WHERE day < (CURRENT_TIMESTAMP AT TIME ZONE 'Europe/Warsaw')::date;
DELETE FROM analytics.request_dedup WHERE created_at <= CURRENT_TIMESTAMP - INTERVAL '47 hours';
COMMIT;
