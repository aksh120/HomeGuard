-- Force PostgREST to rebuild its schema cache.
--
-- The previous migrations relied on NOTIFY pgrst being delivered, but `supabase db push` connects
-- through the pooler, where NOTIFY is not reliably delivered to PostgREST's listening session. The
-- symptom was tables resolving fine while every /rpc/ path returned "requested path is invalid",
-- including functions that had worked earlier.
--
-- The NOTIFY is sent several times because PostgREST debounces reloads and may miss a single
-- notification. This migration changes no schema and is safe to re-run.
NOTIFY pgrst, 'reload schema';
NOTIFY pgrst, 'reload schema';
NOTIFY pgrst, 'reload schema';