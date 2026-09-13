BEGIN;
CREATE SCHEMA IF NOT EXISTS rtc_api;

CREATE TABLE IF NOT EXISTS rtc_api.media (
  id uuid PRIMARY KEY,
  owner_id text NOT NULL,
  filename text NOT NULL UNIQUE CHECK (filename ~ '^[0-9a-f-]{36}\.webp$'),
  width integer NOT NULL CHECK (width > 0),
  height integer NOT NULL CHECK (height > 0),
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS rtc_api.posts (
  id uuid PRIMARY KEY,
  author_id text NOT NULL,
  body text NOT NULL CHECK (char_length(body) BETWEEN 1 AND 4000),
  media_id uuid UNIQUE REFERENCES rtc_api.media(id),
  created_at timestamptz NOT NULL DEFAULT now(),
  deleted_at timestamptz
);
CREATE INDEX IF NOT EXISTS posts_cursor_idx ON rtc_api.posts(created_at DESC, id DESC) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS posts_author_cursor_idx ON rtc_api.posts(author_id, created_at DESC, id DESC) WHERE deleted_at IS NULL;
CREATE TABLE IF NOT EXISTS rtc_api.post_votes (
  post_id uuid NOT NULL REFERENCES rtc_api.posts(id) ON DELETE CASCADE,
  user_id text NOT NULL,
  PRIMARY KEY(post_id, user_id)
);
CREATE TABLE IF NOT EXISTS rtc_api.reports (
  id uuid PRIMARY KEY,
  reporter_id text NOT NULL,
  body text NOT NULL CHECK (char_length(body) BETWEEN 20 AND 4000),
  category text NOT NULL CHECK (category IN ('APP_SUPPORT', 'INFRASTRUCTURE')),
  priority text NOT NULL CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
  status text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'WORKING', 'RESOLVED')),
  latitude double precision CHECK (latitude BETWEEN -90 AND 90),
  longitude double precision CHECK (longitude BETWEEN -180 AND 180),
  created_at timestamptz NOT NULL DEFAULT now(),
  CHECK ((latitude IS NULL) = (longitude IS NULL))
);
CREATE INDEX IF NOT EXISTS reports_owner_idx ON rtc_api.reports(reporter_id, created_at DESC, id DESC);
-- Separate private queues. No resident HTTP endpoint exposes queue contents.
CREATE TABLE IF NOT EXISTS rtc_api.it_queue (
  report_id uuid PRIMARY KEY REFERENCES rtc_api.reports(id) ON DELETE CASCADE,
  enqueued_at timestamptz NOT NULL DEFAULT now(),
  claimed_at timestamptz,
  completed_at timestamptz
);
CREATE TABLE IF NOT EXISTS rtc_api.municipal_queue (
  report_id uuid PRIMARY KEY REFERENCES rtc_api.reports(id) ON DELETE CASCADE,
  enqueued_at timestamptz NOT NULL DEFAULT now(),
  claimed_at timestamptz,
  completed_at timestamptz
);
CREATE TABLE IF NOT EXISTS rtc_api.providers (
  id uuid PRIMARY KEY,
  name text NOT NULL CHECK (char_length(name) BETWEEN 1 AND 200),
  image_url text CHECK (image_url IS NULL OR image_url ~ '^https://'),
  rate_cents bigint NOT NULL CHECK (rate_cents BETWEEN 0 AND 9007199254740991),
  latitude double precision CHECK (latitude BETWEEN -90 AND 90),
  longitude double precision CHECK (longitude BETWEEN -180 AND 180),
  active boolean NOT NULL DEFAULT true,
  CHECK ((latitude IS NULL) = (longitude IS NULL))
);
CREATE TABLE IF NOT EXISTS rtc_api.bookings (
  id uuid PRIMARY KEY,
  user_id text NOT NULL,
  provider_id uuid NOT NULL REFERENCES rtc_api.providers(id),
  starts_at timestamptz NOT NULL,
  notes text NOT NULL CHECK (char_length(notes) <= 2000),
  status text NOT NULL DEFAULT 'PENDING' CHECK (status = 'PENDING'),
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT bookings_provider_slot_key UNIQUE(provider_id, starts_at)
);
CREATE INDEX IF NOT EXISTS bookings_user_idx ON rtc_api.bookings(user_id, created_at DESC);
-- Insert/operation/response all commit together. The unique key serializes competitors.
-- Keep keys for as long as an Android outbox can retry; do not expire blindly.
CREATE TABLE IF NOT EXISTS rtc_api.idempotency (
  user_id text NOT NULL,
  key uuid NOT NULL,
  request_hash text NOT NULL CHECK (length(request_hash) = 64),
  response_status integer,
  response_body jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(user_id, key),
  CHECK ((response_status IS NULL) = (response_body IS NULL))
);
CREATE TABLE IF NOT EXISTS rtc_api.rate_limits (
  user_id text NOT NULL,
  scope text NOT NULL,
  bucket bigint NOT NULL,
  hits integer NOT NULL CHECK(hits > 0),
  PRIMARY KEY(user_id, scope)
);
-- Use the dedicated runtime database role below; never expose its credential to Android.
COMMIT;
