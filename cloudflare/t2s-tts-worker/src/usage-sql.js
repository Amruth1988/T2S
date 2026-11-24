export const RESERVE_USAGE_SQL = `
  INSERT INTO user_provider_usage
    (provider, uid_hash, day, daily_characters, daily_requests, month, monthly_characters, updated_at)
  VALUES (?, ?, ?, ?, 1, ?, ?, ?)
  ON CONFLICT(provider, uid_hash) DO UPDATE SET
    day = excluded.day,
    daily_characters = CASE
      WHEN user_provider_usage.day = excluded.day
        THEN user_provider_usage.daily_characters + excluded.daily_characters
      ELSE excluded.daily_characters END,
    daily_requests = CASE
      WHEN user_provider_usage.day = excluded.day
        THEN user_provider_usage.daily_requests + 1
      ELSE 1 END,
    month = excluded.month,
    monthly_characters = CASE
      WHEN user_provider_usage.month = excluded.month
        THEN user_provider_usage.monthly_characters + excluded.monthly_characters
      ELSE excluded.monthly_characters END,
    updated_at = excluded.updated_at
  WHERE
    (user_provider_usage.day != excluded.day OR
      (user_provider_usage.daily_characters + excluded.daily_characters <= 10000
        AND user_provider_usage.daily_requests + 1 <= 30))
    AND
    (user_provider_usage.month != excluded.month OR
      user_provider_usage.monthly_characters + excluded.monthly_characters <= 100000)
`;

export const DELETE_USAGE_SQL = "DELETE FROM user_provider_usage WHERE uid_hash = ?";

export const RESERVE_SARVAM_USER_SQL = `
  INSERT INTO sarvam_user_usage (uid_hash, day, calls, characters, updated_at)
  VALUES (?, ?, 1, ?, ?)
  ON CONFLICT(uid_hash, day) DO UPDATE SET
    calls = sarvam_user_usage.calls + 1,
    characters = sarvam_user_usage.characters + excluded.characters,
    updated_at = excluded.updated_at
  WHERE sarvam_user_usage.calls + 1 <= 10
    AND sarvam_user_usage.characters + excluded.characters <= 10000
`;
