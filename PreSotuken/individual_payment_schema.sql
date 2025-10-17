-- 個別会計機能用のデータベース変更スクリプト
-- Payment テーブルに個別会計機能用のカラムを追加

-- 重要: 個別会計機能により、1つのvisitに対して複数のpaymentレコードが存在する可能性があります
-- 元の会計（parent_payment_id = NULL）と子会計（parent_payment_id != NULL）を区別するため、
-- visitIdで検索する際は parent_payment_id IS NULL の条件を追加してください

-- 親会計ID: 元の会計を分割した場合、元の会計のIDを保持
ALTER TABLE payment ADD COLUMN parent_payment_id INT NULL;
ALTER TABLE payment ADD CONSTRAINT fk_payment_parent 
    FOREIGN KEY (parent_payment_id) REFERENCES payment(payment_id);

-- 会計ステータス: PENDING(未完了), PARTIAL(部分完了), COMPLETED(完了)
ALTER TABLE payment ADD COLUMN payment_status VARCHAR(20) DEFAULT 'COMPLETED';

-- 分割番号: 割り勘や個別会計で何番目の会計か (1から始まる)
ALTER TABLE payment ADD COLUMN split_number INT NULL;

-- 総分割数: 割り勘での総分割数
ALTER TABLE payment ADD COLUMN total_splits INT NULL;

-- インデックスの作成 (パフォーマンス向上)
CREATE INDEX idx_payment_parent ON payment(parent_payment_id);
CREATE INDEX idx_payment_status ON payment(payment_status);

-- PaymentDetail テーブルに個別会計機能用のカラムを追加

-- 支払い済み会計ID: この商品を支払った会計のID
ALTER TABLE payment_detail ADD COLUMN paid_in_payment_id INT NULL;
ALTER TABLE payment_detail ADD CONSTRAINT fk_payment_detail_paid 
    FOREIGN KEY (paid_in_payment_id) REFERENCES payment(payment_id);

-- インデックスの作成
CREATE INDEX idx_payment_detail_paid ON payment_detail(paid_in_payment_id);

-- コメント追加 (MySQLの場合)
ALTER TABLE payment MODIFY COLUMN parent_payment_id INT NULL 
    COMMENT '元の会計ID (分割会計の場合)';
ALTER TABLE payment MODIFY COLUMN payment_status VARCHAR(20) DEFAULT 'COMPLETED' 
    COMMENT '会計ステータス: PENDING/PARTIAL/COMPLETED';
ALTER TABLE payment MODIFY COLUMN split_number INT NULL 
    COMMENT '分割番号 (1から始まる)';
ALTER TABLE payment MODIFY COLUMN total_splits INT NULL 
    COMMENT '総分割数';
ALTER TABLE payment_detail MODIFY COLUMN paid_in_payment_id INT NULL 
    COMMENT 'この商品を支払った会計ID';
