# 領収書印刷機能 (Receipt Printing Feature)

## 概要 (Overview)

このドキュメントは、POS システムに実装された領収書印刷機能の仕様と使用方法を説明します。

## 機能仕様 (Functional Specifications)

### 1. 会計直後の印刷 (Flow ①)

**フロー:**
1. 会計画面で「領収書発行」チェックボックスをオンにする
2. 会計完了ボタンをクリック
3. 自動的に「領収書発行モーダル」が表示される
4. 発行モードを選択:
   - **全額**: 会計全額で領収書を発行
   - **金額指定**: 指定した金額で領収書を発行（残額から比例按分）
5. 発行ボタンをクリック
6. 領収書が印刷される

### 2. 会計履歴からの印刷 (Flow ②)

**フロー:**
1. 会計履歴画面で対象の会計をクリック
2. 詳細モーダルで「領収書管理」セクションを確認
3. 表示内容:
   - 残高（10%対象、8%対象、合計）
   - 発行済み領収書一覧
   - 各領収書の詳細（番号、金額、発行者、再印字回数、状態）
4. 操作:
   - **新規発行**: 残高がある場合に表示
   - **再印字**: 有効な領収書に対して実行可能
   - **取消**: 有効な領収書を取り消し（印刷不可に）

### 3. 2枚目以降の発行フロー

**動作:**
1. 会計合計を登録（10%対象 / 8%対象の税込金額）
2. 領収書発行時、残額から割合を計算:
   ```
   p10 = R10残 / (R10残 + R08残)
   p08 = R08残 / (R10残 + R08残)
   ```
3. 発行金額をこの割合で配分
4. 内税逆算で税抜・税額を計算
5. 発行済み分を残額から差し引く
6. 次回以降は更新された残額で再計算

## データベース仕様 (Database Schema)

### receipt テーブル

```sql
CREATE TABLE receipt (
    receipt_id INT AUTO_INCREMENT PRIMARY KEY,
    payment_id INT NOT NULL,
    net_amount_10 DECIMAL(10,2) NOT NULL DEFAULT 0,  -- 税抜金額（10%対象）
    net_amount_8 DECIMAL(10,2) NOT NULL DEFAULT 0,   -- 税抜金額（8%対象）
    tax_amount_10 DECIMAL(10,2) NOT NULL DEFAULT 0,  -- 税額（10%対象）
    tax_amount_8 DECIMAL(10,2) NOT NULL DEFAULT 0,   -- 税額（8%対象）
    total_amount DECIMAL(10,2) NOT NULL,             -- 合計金額（税込）
    issued_by INT NOT NULL,                          -- 発行者（user_id）
    issued_at DATETIME NOT NULL,                     -- 発行日時
    receipt_no VARCHAR(50) NOT NULL UNIQUE,          -- 印字番号
    reprint_count INT NOT NULL DEFAULT 0,            -- 再印字回数
    voided BOOLEAN NOT NULL DEFAULT FALSE,           -- 取消フラグ
    voided_at DATETIME,                              -- 取消日時
    voided_by INT,                                   -- 取消者（user_id）
    idempotency_key VARCHAR(100),                    -- 二重発行防止キー
    FOREIGN KEY (payment_id) REFERENCES payment(payment_id),
    FOREIGN KEY (issued_by) REFERENCES user(user_id),
    FOREIGN KEY (voided_by) REFERENCES user(user_id),
    INDEX idx_payment_id (payment_id),
    INDEX idx_receipt_no (receipt_no),
    INDEX idx_idempotency_key (idempotency_key)
);
```

### フィールド説明

- **receipt_id**: 領収書の一意識別子
- **payment_id**: 関連する会計ID
- **net_amount_10**: 10%対象の税抜金額
- **net_amount_8**: 8%対象の税抜金額
- **tax_amount_10**: 10%対象の税額
- **tax_amount_8**: 8%対象の税額
- **total_amount**: 領収書の合計金額（税込）
- **issued_by**: 発行者のユーザーID
- **issued_at**: 発行日時
- **receipt_no**: 印字番号（例: 20240101-0001）
- **reprint_count**: 再印字回数
- **voided**: 取消フラグ（TRUE=取消済み）
- **voided_at**: 取消日時
- **voided_by**: 取消者のユーザーID
- **idempotency_key**: 二重発行防止用のキー

## 按分アルゴリズム (Allocation Algorithm)

### AMOUNTモード（金額指定）

1. **割合計算:**
   ```
   p10 = R10残 / (R10残 + R08残)
   p08 = R08残 / (R10残 + R08残)
   ```

2. **金額按分:**
   ```
   A10 = R × p10 (四捨五入)
   A08 = R - A10
   ```

3. **内税逆算:**
   ```
   10%: net10 = round(A10/1.10), tax10 = A10 - net10
   8%:  net08 = round(A08/1.08), tax08 = A08 - net08
   ```

4. **誤差調整:**
   - ±1円のズレは税額側で調整
   - 優先順位: 10% → 8%

### 丸め規則

- **基本ルール**: HALF_UP（四捨五入）
- **適用箇所**:
  - 金額按分時
  - 税抜金額計算時
  - 最終的な税額調整時

## 印刷仕様 (Print Specifications)

### プリンタ連携

- **方式**: ePOS-Print（HTTPS/XML）
- **プリンタ設定**: 店舗ごとにレシート出力用プリンタを指定

### 印刷レイアウト

```
━━━━━━━━━━━━━━━━━━━
        領収書
━━━━━━━━━━━━━━━━━━━
発行店舗: ○○店
発行日時: 2024/01/01 12:00
領収書番号: 20240101-0001
【再印字 1回目】 ※再印字時のみ
━━━━━━━━━━━━━━━━━━━

    合計金額（税込）
      ¥10,000

━━━━━━━━━━━━━━━━━━━
【税率別内訳】
10%対象
  税抜          ¥8,000
  税額          ¥800
  税込          ¥8,800

8%対象
  税抜          ¥1,111
  税額          ¥89
  税込          ¥1,200

━━━━━━━━━━━━━━━━━━━
合計（検算用）   ¥10,000
━━━━━━━━━━━━━━━━━━━
会計ID: 123
領収書ID: 456
発行者: 山田太郎

[QRコード: PDF417]
領収書番号が埋め込まれています
━━━━━━━━━━━━━━━━━━━
```

### 印刷要素

1. **ヘッダー**:
   - タイトル「領収書」（倍角、中央揃え）
   - 店名
   - 発行日時
   - 印字番号
   - 再印字フラグ（該当時のみ）

2. **本文**:
   - 合計金額（倍角、中央揃え）
   - 税率別内訳（10% / 8%）
   - 各税率の税抜・税額・税込
   - 検算用合計

3. **フッター**:
   - 会計ID
   - 領収書ID
   - 発行者名
   - QRコード（PDF417、領収書番号）

## API仕様 (API Specifications)

### POST /receipts/issue

**リクエスト:**
```json
{
  "paymentId": 123,
  "mode": "FULL" | "AMOUNT",
  "amount": 5000,  // AMOUNTモード時のみ
  "issuedByUserId": 10,
  "idempotencyKey": "unique-key-123"
}
```

**レスポンス:**
```json
{
  "receiptId": 456,
  "paymentId": 123,
  "netAmount10": 4545.45,
  "netAmount8": 463.00,
  "taxAmount10": 454.55,
  "taxAmount8": 37.00,
  "totalAmount": 5500.00,
  "issuedByUserId": 10,
  "issuedByUserName": "山田太郎",
  "issuedAt": "2024-01-01T12:00:00",
  "receiptNo": "20240101-0001",
  "reprintCount": 0,
  "voided": false
}
```

### POST /receipts/{receiptId}/reprint

**リクエスト:**
```json
{
  "userId": 10
}
```

**レスポンス:**
```json
{
  // 同上、reprintCount が増加
  "reprintCount": 1
}
```

### POST /receipts/{receiptId}/void

**リクエスト:**
```json
{
  "userId": 10
}
```

**レスポンス:**
```
200 OK
```

### GET /receipts/payment/{paymentId}

**レスポンス:**
```json
[
  {
    "receiptId": 456,
    // 領収書情報の配列
  }
]
```

### GET /receipts/payment/{paymentId}/remaining

**レスポンス:**
```json
{
  "remaining10": 2000.00,
  "remaining8": 1000.00,
  "totalRemaining": 3000.00
}
```

## 運用ポイント (Operational Points)

### 1. 二重発行防止

- idempotencyKeyを使用
- 同じキーでの再リクエストは既存の領収書を返す
- フロントエンドで一意なキーを生成（タイムスタンプ + ランダム値）

### 2. 取消処理

- 取消済み領収書は印刷不可
- 履歴画面でのみ管理（印刷はしない）
- 取消日時と取消者を記録

### 3. 再印字

- 再印字回数をカウント
- 印字時に「【再印字 N回目】」を表示
- 領収書番号は同じものを使用

### 4. ログ記録

- 全操作をログに記録:
  - 発行
  - 再印字
  - 取消
- ログレベル: INFO以上

## セキュリティ (Security)

### 1. 権限管理

- 発行: 会計権限を持つユーザーのみ
- 取消: 管理者権限が望ましい
- 再印字: 会計権限を持つユーザー

### 2. 監査証跡

- 全ての操作を記録
- 発行者と取消者を明示
- タイムスタンプで操作時刻を記録

### 3. データ整合性

- 外部キー制約で関連データを保護
- トランザクション管理で一貫性を確保
- 印刷エラー時もトランザクションは成功

## トラブルシューティング (Troubleshooting)

### 印刷されない場合

1. プリンタ設定を確認
   - 店舗にレシート出力用プリンタが設定されているか
   - プリンタのIPアドレスが正しいか
2. ネットワーク接続を確認
3. プリンタの電源とペーパーを確認
4. ログでエラーメッセージを確認

### 金額が合わない場合

1. 按分計算のログを確認
2. 丸め処理の適用箇所を確認
3. 税率設定が正しいか確認

### 二重発行が発生した場合

1. idempotencyKeyが重複していないか確認
2. フロントエンドのキー生成ロジックを確認
3. データベースで実際の発行状況を確認

## 今後の拡張予定 (Future Enhancements)

- 領収書プレビュー機能
- 複数税率の動的対応
- 領収書テンプレートのカスタマイズ
- 領収書のメール送信・PDF出力
- 領収書の一括再発行機能
