-- 种子数据：演示用预付卡 DEMO001（余额 200）
-- 预付卡开卡 = 预收账款(负债) +200；健康卡开卡不产生我方应收
INSERT INTO card (card_no, card_type, balance, registered, status) VALUES ('DEMO001', 1, 200.00, TRUE, 0);
INSERT INTO card (card_no, card_type, balance, registered, status) VALUES ('HC001', 2, 500.00, TRUE, 0);
INSERT INTO card (card_no, card_type, balance, registered, status) VALUES ('HC999', 2, 100.00, FALSE, 1);
INSERT INTO card_change_log (card_no, card_type, change_type, change_amount, balance, relate_serial, settle_status, finance_subject, subject_delta, operator)
  VALUES ('DEMO001', 1, 0, 200.00, 200.00, NULL, NULL, 'PREPAID_LIABILITY', 200.00, 'seed');
