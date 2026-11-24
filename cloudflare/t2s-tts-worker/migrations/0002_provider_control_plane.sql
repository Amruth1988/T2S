CREATE TABLE IF NOT EXISTS provider_credentials (
  provider TEXT PRIMARY KEY CHECK (provider IN ('sarvam', 'vakyam', 'india')),
  encrypted_key TEXT NOT NULL,
  is_active INTEGER NOT NULL DEFAULT 0 CHECK (is_active IN (0, 1)),
  added_by TEXT NOT NULL,
  updated_by TEXT NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  rotated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS provider_limits (
  provider TEXT PRIMARY KEY CHECK (provider IN ('sarvam', 'vakyam', 'india')),
  enabled INTEGER NOT NULL DEFAULT 0 CHECK (enabled IN (0, 1)),
  daily_characters INTEGER NOT NULL CHECK (daily_characters >= 0),
  monthly_characters INTEGER NOT NULL CHECK (monthly_characters >= 0),
  daily_requests INTEGER NOT NULL CHECK (daily_requests >= 0),
  updated_by TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  CHECK (enabled = 0 OR (daily_characters > 0 AND monthly_characters > 0 AND daily_requests > 0))
);

CREATE TABLE IF NOT EXISTS provider_global_usage (
  provider TEXT NOT NULL CHECK (provider IN ('sarvam', 'vakyam', 'india')),
  period_type TEXT NOT NULL CHECK (period_type IN ('day', 'month')),
  period_key TEXT NOT NULL,
  characters INTEGER NOT NULL DEFAULT 0 CHECK (characters >= 0),
  requests INTEGER NOT NULL DEFAULT 0 CHECK (requests >= 0),
  updated_at TEXT NOT NULL,
  PRIMARY KEY (provider, period_type, period_key)
);

CREATE TABLE IF NOT EXISTS provider_user_usage (
  provider TEXT NOT NULL CHECK (provider IN ('sarvam', 'vakyam', 'india')),
  uid_hash TEXT NOT NULL,
  day TEXT NOT NULL,
  daily_characters INTEGER NOT NULL DEFAULT 0 CHECK (daily_characters >= 0),
  daily_requests INTEGER NOT NULL DEFAULT 0 CHECK (daily_requests >= 0),
  month TEXT NOT NULL,
  monthly_characters INTEGER NOT NULL DEFAULT 0 CHECK (monthly_characters >= 0),
  updated_at TEXT NOT NULL,
  PRIMARY KEY (provider, uid_hash)
);

CREATE INDEX IF NOT EXISTS idx_provider_user_usage_updated_at
  ON provider_user_usage(updated_at);

CREATE TABLE IF NOT EXISTS sarvam_user_usage (
  uid_hash TEXT NOT NULL,
  day TEXT NOT NULL,
  calls INTEGER NOT NULL DEFAULT 0 CHECK (calls >= 0),
  characters INTEGER NOT NULL DEFAULT 0 CHECK (characters >= 0),
  updated_at TEXT NOT NULL,
  PRIMARY KEY (uid_hash, day)
);
