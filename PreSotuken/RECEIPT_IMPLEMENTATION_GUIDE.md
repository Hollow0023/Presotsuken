# 領収書印刷機能 実装ガイド

## 概要
PreSotuken（プレソツケン）飲食店向け注文管理システムに領収書印刷機能を実装しました。
会計完了時または会計履歴から領収書を発行でき、按分計算、再印字、取消機能を備えています。

## 主要機能

### 1. 会計直後の領収書発行
- 会計画面に「領収書印刷」チェックボックスを配置
- チェック有：会計完了後に自動で領収書発行モーダルを表示
- チェック無：通常通り会計完了

### 2. 領収書発行モード
#### 全額モード
- 会計の残高全額で領収書を発行
- 税率別に自動按分

#### 金額指定モード
- 任意の金額を指定して発行
- 比例按分で税率ごとに配分（10%と8%）
- 按分式：
  ```
  p10 = R10残 / (R10残 + R08残)
  p08 = R08残 / (R10残 + R08残)
  A10 = 発行金額 × p10（四捨五入）
  A08 = 発行金額 - A10
  ```

### 3. 会計履歴からの管理
- 会計履歴一覧に「領収書管理」リンクを追加
- 詳細画面で以下を表示：
  - 会計サマリ（10%/8% 税抜・税額・税込）
  - 領収書発行残高（税率別の未発行分）
  - 発行済み領収書一覧
- 操作：
  - 新規領収書発行
  - 再印字（【再印字】フラグ付きで印刷）
  - 取消（履歴は残るが印刷不可）

### 4. 二重発行防止
- idempotencyKeyを使用
- 同じキーでの発行要求は既存の領収書を返却

## データベーススキーマ

### テーブル構造
```sql
CREATE TABLE receipt (
    receipt_id INT AUTO_INCREMENT PRIMARY KEY,
    payment_id INT NOT NULL,
    store_id INT NOT NULL,
    net_amount_10 DOUBLE DEFAULT 0,    -- 10%対象税抜金額
    tax_amount_10 DOUBLE DEFAULT 0,    -- 10%税額
    net_amount_8 DOUBLE DEFAULT 0,     -- 8%対象税抜金額
    tax_amount_8 DOUBLE DEFAULT 0,     -- 8%税額
    user_id INT NOT NULL,              -- 発行者ID
    issued_at DATETIME NOT NULL,       -- 発行日時
    receipt_no VARCHAR(50) NOT NULL UNIQUE,  -- 印字番号
    reprint_count INT DEFAULT 0,       -- 再印字回数
    voided BOOLEAN DEFAULT FALSE,      -- 取消フラグ
    voided_at DATETIME,                -- 取消日時
    voided_by_user_id INT,             -- 取消者ID
    idempotency_key VARCHAR(100) UNIQUE,
    FOREIGN KEY (payment_id) REFERENCES payment(payment_id),
    FOREIGN KEY (store_id) REFERENCES store(store_id),
    FOREIGN KEY (user_id) REFERENCES user(user_id),
    FOREIGN KEY (voided_by_user_id) REFERENCES user(user_id)
);
```

### セットアップ手順
1. `receipt_table_schema.sql` を実行してテーブルを作成
2. アプリケーションを再起動
3. JPA が自動的にエンティティをマッピング

## 実装ファイル

### バックエンド

#### エンティティ
- `src/main/java/com/order/entity/Receipt.java`
  - 領収書発行履歴エンティティ

#### リポジトリ
- `src/main/java/com/order/repository/ReceiptRepository.java`
  - 領収書データアクセス層

#### DTO
- `src/main/java/com/order/dto/ReceiptIssueRequest.java` - 発行リクエスト
- `src/main/java/com/order/dto/ReceiptResponseDto.java` - レスポンス
- `src/main/java/com/order/dto/PaymentSummaryDto.java` - 会計サマリ

#### サービス
- `src/main/java/com/order/service/ReceiptService.java`
  - 業務ロジック（按分計算、発行、再印字、取消）
  - 主要メソッド：
    - `calculatePaymentSummary()` - 税率別集計と残高計算
    - `issueReceipt()` - 領収書発行
    - `reprintReceipt()` - 再印字
    - `voidReceipt()` - 取消

- `src/main/java/com/order/service/PrintService.java`
  - `printReceipt()` - ESC/POS印刷コマンド生成

#### コントローラー
- `src/main/java/com/order/controller/ReceiptController.java`
  - REST API エンドポイント
  - `POST /api/receipts/issue` - 発行
  - `POST /api/receipts/{id}/reprint` - 再印字
  - `POST /api/receipts/{id}/void` - 取消
  - `GET /api/payments/{id}/receipts` - 一覧取得
  - `GET /api/payments/{id}/summary` - サマリ取得

- `src/main/java/com/order/controller/PaymentController.java`
  - `GET /payments/history/detail` - 詳細画面表示

### フロントエンド

#### テンプレート
- `src/main/resources/templates/payment.html`
  - 会計画面（領収書印刷チェックボックスとモーダル追加）
  - 機能：
    - 領収書印刷チェックボックス
    - 発行モーダル（全額/金額指定タブ）
    - タブ切り替えJavaScript

- `src/main/resources/templates/payment-detail.html`
  - 会計履歴詳細画面
  - 機能：
    - 会計サマリ表示
    - 税率別内訳表示
    - 領収書発行残高表示
    - 発行済み領収書一覧
    - 新規発行・再印字・取消ボタン

- `src/main/resources/templates/paymentHistory.html`
  - 会計履歴一覧（「領収書管理」リンク追加）

### テスト
- `src/test/java/com/order/service/ReceiptServiceTest.java`
  - 按分計算のユニットテスト
  - テストケース：
    - 税率10%のみの会計
    - 税率8%のみの会計
    - 税率混在の会計
    - 全額モード発行
    - 金額指定モード按分計算
    - 二重発行防止
    - 発行額超過エラー

## 按分計算アルゴリズム

### 基本原則
1. 会計の税率別金額（10%/8%）を算出
2. 既発行領収書の税率別金額を差し引いて残高を計算
3. 発行金額を残高の比率で按分
4. 按分後の金額から内税逆算で税抜・税額を計算

### 計算式
```java
// 残高から按分比率を計算
BigDecimal ratio10 = remaining10 / (remaining10 + remaining8)
BigDecimal ratio8 = remaining8 / (remaining10 + remaining8)

// 発行金額を按分
BigDecimal amount10 = issueAmount × ratio10 (四捨五入)
BigDecimal amount8 = issueAmount - amount10

// 内税逆算
BigDecimal net10 = amount10 / 1.10 (四捨五入)
BigDecimal tax10 = amount10 - net10
BigDecimal net8 = amount8 / 1.08 (四捨五入)
BigDecimal tax8 = amount8 - net8
```

### 丸め規則
- 全て `RoundingMode.HALF_UP`（四捨五入）を使用
- ±1円のズレは税額側で調整

## 印刷仕様

### ESC/POS コマンド
- ePOS-Print XML形式で生成
- WebSocket経由でフロントエンドに送信

### 印刷内容
```
[店舗ロゴ]

テスト店舗
領収書
【再印字】 ← 再印字の場合のみ

印字番号: R1-20241215-123456
発行日時: 2024/12/15 14:30
発行者: 山田太郎

--------------------------------
合計金額                  1,000円

【内訳】
10%対象(税抜)               455円
10%税額                      45円
10%税込                     500円
8%対象(税抜)                463円
8%税額                       37円
8%税込                      500円

--------------------------------

会計ID: 123
領収書ID: 456
発行者ID: 1

[QRコード: 印字番号]
```

## 運用上のポイント

### セキュリティ
- idempotencyKey による二重発行防止
- 取消済み領収書は再印字不可
- 全操作をログに記録（発行・再印字・取消）

### エラーハンドリング
- 発行額が残高を超える場合はエラー
- 存在しない会計・ユーザーIDでエラー
- 取消済み領収書の再印字はエラー

### パフォーマンス
- 按分計算はメモリ内で完結
- データベースアクセスは最小限
- インデックス設定済み（payment_id, receipt_no, idempotency_key）

## トラブルシューティング

### 問題: 領収書が印刷されない
- プリンター設定を確認（レシート出力用プリンターが設定されているか）
- WebSocket接続を確認
- ブラウザのコンソールログを確認

### 問題: 按分計算の結果が期待と異なる
- 税率設定を確認（商品ごとの税率が正しいか）
- 既発行領収書の取消状態を確認
- ReceiptServiceTest のテストケースで検証

### 問題: 二重発行が発生する
- idempotencyKey が正しく設定されているか確認
- ネットワーク遅延によるタイムアウト後の再送信の可能性

## 今後の拡張案

1. **複数税率対応**
   - 将来的に税率が変更された場合の対応
   - 軽減税率以外の税率にも対応

2. **領収書テンプレートのカスタマイズ**
   - 店舗ごとにレイアウトを変更可能に
   - 宛名・但書の追加

3. **PDF出力**
   - 印刷だけでなくPDFとしてダウンロード可能に
   - メール送信機能

4. **統計機能**
   - 期間ごとの領収書発行枚数
   - 金額別の集計

## 参考資料

- [ESC/POS コマンドリファレンス](https://reference.epson-biz.com/modules/ref_epos_xml/index.php)
- [Spring Boot ドキュメント](https://spring.io/projects/spring-boot)
- [JPA 仕様](https://jakarta.ee/specifications/persistence/)

## ライセンス
このプロジェクトは PreSotuken の一部として管理されています。

---

実装日: 2024年12月
実装者: GitHub Copilot
