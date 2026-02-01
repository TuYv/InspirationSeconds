CREATE TABLE IF NOT EXISTS user_config (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  open_id VARCHAR(64) NOT NULL UNIQUE,
  app_type VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  encrypted_api_key VARCHAR(2048) NOT NULL,
  database_id VARCHAR(128) NOT NULL,
  prompt_config JSON,
  is_guest BOOLEAN DEFAULT FALSE,
  migration_status VARCHAR(20) DEFAULT 'NONE',
  updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS conversation_state (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  open_id VARCHAR(64) NOT NULL UNIQUE,
  step VARCHAR(32) NOT NULL,
  temp_api_key VARCHAR(4096),
  updated_at TIMESTAMP NOT NULL
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_user_config_is_guest ON user_config(is_guest);
