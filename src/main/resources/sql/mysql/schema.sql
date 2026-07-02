-- Jobs plugin MySQL schema
-- spec/05-persistence.md 「MySQL 実装」を参照。
-- SchemaInitializer が起動時に本ファイルを読み実行する。

CREATE TABLE IF NOT EXISTS player_job (
  player_uuid     BINARY(16) NOT NULL,
  job_id          VARCHAR(32) NOT NULL,
  selected_at     DATETIME(3) NOT NULL,
  PRIMARY KEY (player_uuid, selected_at),
  INDEX idx_current (player_uuid, selected_at DESC)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS action_log (
  id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  player_uuid     BINARY(16) NOT NULL,
  job_id          VARCHAR(32) NOT NULL,
  action_key      VARCHAR(128) NOT NULL,
  base_reward     INT NOT NULL,
  final_reward    INT NOT NULL,
  rare_hit        BOOLEAN NOT NULL DEFAULT FALSE,
  amount          INT NOT NULL DEFAULT 1,
  occurred_at     DATETIME(3) NOT NULL,
  INDEX idx_player_time (player_uuid, occurred_at),
  INDEX idx_job_time (job_id, occurred_at),
  INDEX idx_player_job_time (player_uuid, job_id, occurred_at DESC)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS daily_reward_total (
  player_uuid     BINARY(16) NOT NULL,
  reward_date     DATE NOT NULL,
  total_reward    BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (player_uuid, reward_date)
) ENGINE=InnoDB;
