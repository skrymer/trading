-- V9: Add user_settings table for storing application settings in the database.
-- Replaces file-based config (~/.trading-app/config.properties).

CREATE TABLE user_settings (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
  setting_key VARCHAR(100) NOT NULL,
  setting_value TEXT NOT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Global settings (user_id IS NULL): one row per key
CREATE UNIQUE INDEX idx_user_settings_global_key
  ON user_settings (setting_key) WHERE user_id IS NULL;

-- Per-user settings: one row per (user_id, key)
CREATE UNIQUE INDEX idx_user_settings_user_key
  ON user_settings (user_id, setting_key) WHERE user_id IS NOT NULL;
