-- H2 (MODE=MySQL) 演示库；生产 DDL 见 schema-mysql.sql
CREATE TABLE IF NOT EXISTS charge_order (
  charge_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
  serial_no      VARCHAR(32)  NOT NULL,
  customer_name  VARCHAR(64)  NOT NULL,
  total_amount   DECIMAL(12,2) NOT NULL,
  discount       DECIMAL(5,2) NOT NULL DEFAULT 100.00,
  pay_amount     DECIMAL(12,2) NOT NULL,
  charge_status  TINYINT      NOT NULL DEFAULT 0,
  pay_method     TINYINT,
  card_no        VARCHAR(32),
  operator       VARCHAR(32),
  remark         VARCHAR(255),
  created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_charge_serial UNIQUE (serial_no)
);
CREATE INDEX IF NOT EXISTS idx_charge_status ON charge_order(charge_status);

CREATE TABLE IF NOT EXISTS card (
  card_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
  card_no     VARCHAR(32)  NOT NULL,
  card_type   TINYINT      NOT NULL,
  balance     DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  registered  BOOLEAN      NOT NULL DEFAULT FALSE,
  status      TINYINT      NOT NULL DEFAULT 0,
  created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_card_no UNIQUE (card_no)
);

CREATE TABLE IF NOT EXISTS card_change_log (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  card_no         VARCHAR(32)  NOT NULL,
  card_type       TINYINT      NOT NULL,
  change_type     TINYINT      NOT NULL,
  change_amount   DECIMAL(12,2) NOT NULL,
  balance         DECIMAL(12,2) NOT NULL,
  relate_serial   VARCHAR(32),
  settle_status   TINYINT,
  finance_subject VARCHAR(32) NOT NULL,
  subject_delta   DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  operator        VARCHAR(32),
  created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_log_card ON card_change_log(card_no, id);
CREATE INDEX IF NOT EXISTS idx_log_subject ON card_change_log(finance_subject);
CREATE INDEX IF NOT EXISTS idx_log_settle ON card_change_log(settle_status, finance_subject);
