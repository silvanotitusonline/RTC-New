BEGIN;

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES
  ('daily-post-media', 'daily-post-media', false, 52428800, ARRAY['image/jpeg','image/png','image/webp','video/mp4','video/webm']),
  ('daily-post-ai-audio', 'daily-post-ai-audio', false, 10485760, ARRAY['audio/mpeg'])
ON CONFLICT (id) DO UPDATE SET
  public = EXCLUDED.public,
  file_size_limit = EXCLUDED.file_size_limit,
  allowed_mime_types = EXCLUDED.allowed_mime_types;

COMMIT;
