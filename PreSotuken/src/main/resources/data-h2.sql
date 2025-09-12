-- Sample data for testing staff management
INSERT INTO store (store_id, store_name) VALUES (1, 'テスト店舗');

-- Sample users for testing
INSERT INTO user (user_id, store_id, user_name, is_admin) VALUES (1, 1, '管理者ユーザー', true);
INSERT INTO user (user_id, store_id, user_name, is_admin) VALUES (2, 1, '一般スタッフ', false);