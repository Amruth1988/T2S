CREATE TABLE IF NOT EXISTS user_provider_usage (
  provider TEXT NOT NULL CHECK (provider IN ('india', 'vakyam')),
  uid_hash TEXT NOT NULL,
  day TEXT NOT NULL,
  daily_characters INTEGER NOT NULL CHECK (daily_characters >= 0),
  daily_requests INTEGER NOT NULL CHECK (daily_requests >= 0),
  month TEXT NOT NULL,
  monthly_characters INTEGER NOT NULL CHECK (monthly_characters >= 0),
  updated_at TEXT NOT NULL,
  PRIMARY KEY (provider, uid_hash)
);

CREATE INDEX IF NOT EXISTS idx_user_provider_usage_updated_at
  ON user_provider_usage(updated_at);
