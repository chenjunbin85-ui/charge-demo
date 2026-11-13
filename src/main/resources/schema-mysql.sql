-- 生产 MySQL 8 DDL（与 H2 演示库字段一致，补充引擎/注释/字符集）
-- 切换方式：application.yml 换 datasource.url 为 jdbc:mysql://...，并关闭 spring.sql.init
CREATE DATABASE IF NOT EXISTS charge_demo DEFAULT CHARACTER SET utf8mb4;
USE charge_demo;

CREATE TABLE charge_order (
  charge_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
  serial_no      VARCHAR(32)  NOT NULL COMMENT '人员登记流水号',
  customer_name  VARCHAR(64)  NOT NULL,
  total_amount   DECIMAL(12,2) NOT NULL COMMENT '应收总额',
  discount       DECIMAL(5,2) NOT NULL DEFAULT 100.00 COMMENT '折扣(%)',
  pay_amount     DECIMAL(12,2) NOT NULL COMMENT '折后需付，后端计算，前端只读',
  charge_status  TINYINT      NOT NULL DEFAULT 0 COMMENT '0未收费 1已收费 2记账 3已退费',
  pay_method     TINYINT      COMMENT '1预付卡 2电子健康卡',
  card_no        VARCHAR(32),
  operator       VARCHAR(32),
  remark         VARCHAR(255),
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_charge_serial (serial_no),
  KEY idx_charge_status (charge_status)
) ENGINE=InnoDB;

CREATE TABLE card (
  card_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
  card_no    VARCHAR(32)  NOT NULL,
  card_type  TINYINT      NOT NULL COMMENT '1预付卡(自营储值) 2电子健康卡(第三方代收)',
  balance    DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  registered BOOLEAN NOT NULL DEFAULT FALSE,
  status     TINYINT NOT NULL DEFAULT 0 COMMENT '0正常 1作废',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_card_no (card_no)
) ENGINE=InnoDB;

CREATE TABLE card_change_log (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  card_no         VARCHAR(32)  NOT NULL,
  card_type       TINYINT      NOT NULL,
  change_type     TINYINT      NOT NULL COMMENT '0新建 1充值 2消费 3退费 4作废 5结算',
  change_amount   DECIMAL(12,2) NOT NULL COMMENT '卡余额变动，带符号',
  balance         DECIMAL(12,2) NOT NULL COMMENT '变动后卡余额',
  relate_serial   VARCHAR(32)  COMMENT '关联收费流水号 / 结算批次号',
  settle_status   TINYINT      COMMENT '健康卡 0待结算 1已结算；预付卡 NULL',
  finance_subject VARCHAR(32)  NOT NULL COMMENT 'PREPAID_LIABILITY / EHC_RECEIVABLE，严禁并账',
  subject_delta   DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '该笔对财务科目的影响，方向按科目定',
  operator        VARCHAR(32),
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_log_card (card_no, id),
  KEY idx_log_subject (finance_subject),
  KEY idx_log_settle (settle_status, finance_subject)
) ENGINE=InnoDB;
