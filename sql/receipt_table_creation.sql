-- 領収書発行履歴テーブルの作成
-- Receipt issuance history table
CREATE TABLE receipt (
    receipt_id INT AUTO_INCREMENT PRIMARY KEY,
    payment_id INT NOT NULL,
    net_amount_10 DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '税抜金額（10%対象）',
    net_amount_8 DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '税抜金額（8%対象）',
    tax_amount_10 DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '税額（10%対象）',
    tax_amount_8 DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '税額（8%対象）',
    total_amount DECIMAL(10,2) NOT NULL COMMENT '合計金額（税込）',
    issued_by INT NOT NULL COMMENT '発行者（user_id）',
    issued_at DATETIME NOT NULL COMMENT '発行日時',
    receipt_no VARCHAR(50) NOT NULL UNIQUE COMMENT '印字番号',
    reprint_count INT NOT NULL DEFAULT 0 COMMENT '再印字回数',
    voided BOOLEAN NOT NULL DEFAULT FALSE COMMENT '取消フラグ',
    voided_at DATETIME COMMENT '取消日時',
    voided_by INT COMMENT '取消者（user_id）',
    idempotency_key VARCHAR(100) COMMENT '二重発行防止キー',
    FOREIGN KEY (payment_id) REFERENCES payment(payment_id),
    FOREIGN KEY (issued_by) REFERENCES user(user_id),
    FOREIGN KEY (voided_by) REFERENCES user(user_id),
    INDEX idx_payment_id (payment_id),
    INDEX idx_receipt_no (receipt_no),
    INDEX idx_idempotency_key (idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='領収書発行履歴';

-- 使用例：
-- このテーブルに領収書の発行履歴が記録されます
-- payment_idで会計データと関連付けられます
-- net_amount_10, net_amount_8: 税率別の税抜金額
-- tax_amount_10, tax_amount_8: 税率別の税額
-- total_amount: 領収書の合計金額（税込）
-- receipt_no: 日付+通番形式の印字番号（例: 20240101-0001）
-- reprint_count: 再印字した回数
-- voided: 取り消された領収書はTRUEになります（印字はされません）
-- idempotency_key: 同じリクエストが複数回来ても重複発行を防ぐためのキー
