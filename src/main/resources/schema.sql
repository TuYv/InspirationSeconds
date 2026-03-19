CREATE TABLE IF NOT EXISTS user_config (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  open_id VARCHAR(64) NOT NULL UNIQUE,
  nickname VARCHAR(128),
  avatar_url VARCHAR(512),
  app_type VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  encrypted_api_key VARCHAR(2048) NOT NULL,
  database_id VARCHAR(128) NOT NULL,
  prompt_config JSON,
  is_guest BOOLEAN DEFAULT FALSE,
  migration_status VARCHAR(20) DEFAULT 'NONE',
  tasks_database_id VARCHAR(128),
  ai_base_url       VARCHAR(512),
  ai_api_key        VARCHAR(2048),
  ai_model          VARCHAR(256),
  updated_at TIMESTAMP NOT NULL
);


-- 索引
CREATE INDEX IF NOT EXISTS idx_user_config_is_guest ON user_config(is_guest);

CREATE TABLE IF NOT EXISTS task_draft (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  open_id       VARCHAR(64)   NOT NULL,
  draft_json    JSON          NOT NULL,
  conversation_log JSON       NOT NULL,
  status        VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_task_draft_open_id_status ON task_draft(open_id, status);

CREATE TABLE IF NOT EXISTS token_usage (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  open_id           VARCHAR(64)  NOT NULL,
  usage_date        DATE         NOT NULL,
  prompt_tokens     INT          NOT NULL DEFAULT 0,
  completion_tokens INT          NOT NULL DEFAULT 0,
  total_tokens      INT          NOT NULL DEFAULT 0,
  call_count        INT          NOT NULL DEFAULT 0,
  CONSTRAINT uk_open_id_date UNIQUE (open_id, usage_date)
);

CREATE TABLE IF NOT EXISTS themes (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  name          VARCHAR(100)  NOT NULL,
  description   TEXT,
  css           LONGTEXT      NOT NULL,
  preview_md    TEXT,
  author_name   VARCHAR(50)   NOT NULL DEFAULT '匿名',
  thumbnail_url VARCHAR(500),
  star_count    INT           NOT NULL DEFAULT 0,
  is_builtin    BOOLEAN       NOT NULL DEFAULT FALSE,
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
);
