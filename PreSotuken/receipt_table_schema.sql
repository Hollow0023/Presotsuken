-- 領収書印刷機能に必要なデータベース変更
-- 
-- 以下のSQLを実行して領収書発行履歴テーブルを作成してください

-- 領収書発行履歴テーブル
CREATE TABLE receipt (
    receipt_id INT AUTO_INCREMENT PRIMARY KEY COMMENT '領収書ID（主キー）',
    payment_id INT NOT NULL COMMENT '会計ID（外部キー: payment.payment_id）',
    store_id INT NOT NULL COMMENT '店舗ID（外部キー: store.store_id）',
    
    -- 税率別の金額（10%対象）
    net_amount_10 DOUBLE DEFAULT 0 COMMENT '税抜金額（10%対象）',
    tax_amount_10 DOUBLE DEFAULT 0 COMMENT '税額（10%対象）',
    
    -- 税率別の金額（8%対象）
    net_amount_8 DOUBLE DEFAULT 0 COMMENT '税抜金額（8%対象）',
    tax_amount_8 DOUBLE DEFAULT 0 COMMENT '税額（8%対象）',
    
    -- 発行者情報
    user_id INT NOT NULL COMMENT '発行者ID（外部キー: user.user_id）',
    issued_at DATETIME NOT NULL COMMENT '発行日時',
    
    -- 印字管理
    receipt_no VARCHAR(50) NOT NULL UNIQUE COMMENT '印字番号（日付＋通番など、一意）',
    reprint_count INT DEFAULT 0 COMMENT '再印字回数',
    
    -- 取消管理
    voided BOOLEAN DEFAULT FALSE COMMENT '取消フラグ',
    voided_at DATETIME COMMENT '取消日時',
    voided_by_user_id INT COMMENT '取消者ID（外部キー: user.user_id）',
    
    -- 二重発行防止
    idempotency_key VARCHAR(100) UNIQUE COMMENT 'idempotencyKey（二重発行防止用、一意）',
    
    -- 外部キー制約
    FOREIGN KEY (payment_id) REFERENCES payment(payment_id) ON DELETE CASCADE,
    FOREIGN KEY (store_id) REFERENCES store(store_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES user(user_id) ON DELETE RESTRICT,
    FOREIGN KEY (voided_by_user_id) REFERENCES user(user_id) ON DELETE RESTRICT,
    
    -- インデックス
    INDEX idx_payment_id (payment_id),
    INDEX idx_store_id (store_id),
    INDEX idx_issued_at (issued_at),
    INDEX idx_receipt_no (receipt_no),
    INDEX idx_idempotency_key (idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='領収書発行履歴テーブル';

-- 使用例とデータ説明：
-- 
-- 1. 領収書発行時：
--    - payment_id: 会計IDを指定
--    - net_amount_10, tax_amount_10: 10%対象の税抜金額と税額
--    - net_amount_8, tax_amount_8: 8%対象の税抜金額と税額
--    - user_id: 発行者のユーザーID
--    - issued_at: 発行日時（現在時刻）
--    - receipt_no: 印字番号（例: R1-20241215-123456）
--    - idempotency_key: 二重発行防止キー（例: {payment_id}-{timestamp}）
--
-- 2. 再印字時：
--    - reprint_count: 再印字のたびにインクリメント
--    - 印字時に【再印字】フラグを表示
--
-- 3. 取消時：
--    - voided: TRUE に設定
--    - voided_at: 取消日時を記録
--    - voided_by_user_id: 取消者のIDを記録
--    - 取消済みの領収書は印字しない（履歴画面でのみ表示）
--
-- 4. 按分計算：
--    - 会計の税率別残高から、発行額を比例按分して税率ごとに配分
--    - 按分式: A10 = R × (R10残/(R10残+R08残))、A08 = R - A10
--    - 税抜・税額の逆算: net = round(gross/1.1)、tax = gross - net
--
-- 5. 運用ポイント：
--    - 会計直後と履歴から両方発行可能
--    - 二重発行防止にidempotency_keyを利用
--    - 全操作をログに記録（発行・再印字・取消）
--    - 丸め規則：HALF_UP（四捨五入）を使用
