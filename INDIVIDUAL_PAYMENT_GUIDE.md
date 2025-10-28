# 個別会計機能 実装ガイド

## 概要
このドキュメントは、PreSotsuken注文管理システムに実装された個別会計機能について説明します。
個別会計機能は、1つの会計を複数回に分けて支払うための機能で、以下の2つのモードがあります。

### 1. 割り勘会計
合計金額を指定人数で均等に分割し、1人ずつ会計を行います。

### 2. 個別会計
商品単位で選択し、選択した商品のみを会計します。

## 機能仕様

### 割り勘会計の動作

1. **分割人数の指定**: 何人で割るかを指定
2. **金額の自動計算**: 合計金額 ÷ 人数 (切り捨て)
3. **余りの処理**: 端数は最後の会計に含める
4. **順次会計**: 1人目、2人目...と順番に会計を実行
5. **完了判定**: 最後の会計が完了した時点で退店時刻を記録

#### 計算例
- 合計金額: 3,300円
- 分割人数: 3人
- 1人目: 1,100円
- 2人目: 1,100円
- 3人目: 1,100円 (3,300 - 1,100 × 2 = 1,100)

### 個別会計の動作

1. **商品選択**: チェックボックスで支払う商品を選択
2. **金額計算**: 選択商品の税込み合計を自動計算
3. **割引適用**: 個別の割引も可能
4. **支払い実行**: 選択商品を支払い済みとしてマーク
5. **完了判定**: 全商品が支払い済みになった時点で退店時刻を記録

## データベース設計

### 重要: Visit と Payment の関係について

個別会計機能により、**1つのvisitに対して複数のpaymentレコードが存在する可能性があります**。

- **元の会計（親会計）**: `parent_payment_id` が NULL
- **子会計**: `parent_payment_id` が親会計のIDを参照

**visitIdでPaymentを検索する際の注意点:**
- 必ず `parent_payment_id IS NULL` の条件を追加してください
- 新しいリポジトリメソッド `findByVisitVisitIdAndParentPaymentIsNull()` を使用してください
- 既存の `findByVisitVisitId()` を使うと `IncorrectResultSizeDataAccessException` が発生します

### Payment テーブルの拡張

| カラム名 | データ型 | 説明 |
|---------|---------|------|
| parent_payment_id | INT | 元の会計ID (分割会計の場合) |
| payment_status | VARCHAR(20) | 会計ステータス (PENDING/PARTIAL/COMPLETED) |
| split_number | INT | 分割番号 (1から始まる) |
| total_splits | INT | 総分割数 |

### PaymentDetail テーブルの拡張

| カラム名 | データ型 | 説明 |
|---------|---------|------|
| paid_in_payment_id | INT | この商品を支払った会計のID |

### リレーション図

```
Payment (元の会計)
├─ payment_status: "PARTIAL"
└─ 子会計1 (parent_payment_id -> 元の会計)
   ├─ split_number: 1
   └─ payment_status: "PARTIAL"
└─ 子会計2 (parent_payment_id -> 元の会計)
   ├─ split_number: 2
   └─ payment_status: "COMPLETED" (最後)

→ 子会計2完了時に元の会計も "COMPLETED" に変更
→ Visit の leave_time を記録
```

## API エンドポイント

### 1. 割り勘会計処理
```
POST /payments/split
```

**リクエストボディ:**
```json
{
  "paymentId": 1,
  "numberOfSplits": 3,
  "currentSplit": 1,
  "paymentTypeId": 1,
  "cashierId": 1,
  "deposit": 2000,
  "paymentTime": "2025-10-14T10:30:00"
}
```

**レスポンス:**
```json
{
  "success": true,
  "paymentId": 101,
  "amount": 1100,
  "completed": false
}
```

### 2. 個別会計処理
```
POST /payments/individual
```

**リクエストボディ:**
```json
{
  "paymentId": 1,
  "paymentDetailIds": [1, 2, 3],
  "paymentTypeId": 1,
  "cashierId": 1,
  "discount": 0,
  "deposit": 1500,
  "paymentTime": "2025-10-14T10:30:00"
}
```

**レスポンス:**
```json
{
  "success": true,
  "paymentId": 102,
  "amount": 1450,
  "completed": false
}
```

### 3. 残り会計情報取得
```
GET /payments/{paymentId}/remaining
```

**レスポンス:**
```json
{
  "paymentId": 1,
  "totalAmount": 3300,
  "paidAmount": 1100,
  "remainingAmount": 2200,
  "isFullyPaid": false,
  "unpaidDetails": [
    {
      "paymentDetailId": 2,
      "menuName": "餃子",
      "quantity": 1,
      "price": 500,
      "taxRate": 0.10,
      "subtotal": 500,
      "discount": 0,
      "totalWithTax": 550
    }
  ],
  "childPayments": [
    {
      "paymentId": 101,
      "splitNumber": 1,
      "amount": 1100,
      "paymentTypeName": "現金",
      "cashierName": "田中"
    }
  ]
}
```

## フロントエンド実装

### 割り勘会計モーダル

- **分割人数入力**: 2人以上を指定
- **1人あたりの金額表示**: 自動計算
- **現在の会計選択**: 何人目の会計かを選択
- **今回の支払額表示**: 最後は余りを含めた金額を表示

### 個別会計モーダル

- **商品選択テーブル**: チェックボックスで商品を選択
- **選択合計の表示**: リアルタイムで合計金額を更新
- **割引入力**: 個別の割引額を指定可能

## 使用方法

### 割り勘会計の操作手順

1. 会計画面で「割り勘会計」ボタンをクリック
2. 分割人数を入力 (デフォルトは来店人数)
3. 現在何人目の会計かを選択
4. 支払い種別と担当者を選択
5. 必要に応じて預かり金額を入力
6. 「会計実行」ボタンをクリック
7. 次の人の会計を続ける (または終了)

### 個別会計の操作手順

1. 会計画面で「個別会計」ボタンをクリック
2. 支払う商品にチェックを入れる
3. 支払い種別と担当者を選択
4. 必要に応じて割引と預かり金額を入力
5. 「会計実行」ボタンをクリック
6. 残りの商品がある場合は繰り返す

## エラーハンドリング

### 考慮すべきエラーケース

1. **既に支払い済みの商品を再度支払おうとした場合**
   - エラーメッセージを表示
   - 個別会計モーダルで支払い済み商品を非表示にする (今後の拡張)

2. **支払い種別または担当者が未選択**
   - 会計実行前にバリデーション

3. **ネットワークエラー**
   - エラーメッセージを表示し、再試行を促す

## テスト

### 単体テスト

`PaymentSplitServiceTest.java` で以下をカバー:

- 割り勘計算 (3人で割る)
- 余りの処理 (最後の会計に含める)
- 個別会計 (一部商品の支払い)
- 全商品支払い後の完了判定
- 重複支払いのチェック

### テストコマンド

```bash
./gradlew test --tests PaymentSplitServiceTest
```

## 会計履歴の表示ルール

会計履歴ページでは、割り勘会計と個別会計で異なる表示ルールを適用しています。

### 表示ロジック

#### 1. 割り勘会計 (totalSplits > 0)
- **親会計のみ表示**: 合計金額と分割情報が親会計に集約されるため
- **子会計は非表示**: 各人の支払い詳細は親会計の詳細ページで確認可能

#### 2. 個別会計 (totalSplits = null)
- **親会計は非表示**: 商品が分割されているため合計が意味を持たない
- **子会計のみ表示**: 実際に支払われた会計レコードを個別に表示

#### 3. 通常会計
- **そのまま表示**: 分割されていない通常の会計

### 実装詳細

フィルタリングロジックは `PaymentController.showPaymentHistory()` で実装されています:

```java
// 親会計が存在しない場合（親会計または通常の会計）
if (p.getParentPayment() == null) {
    List<Payment> children = paymentRepository.findByParentPaymentPaymentId(p.getPaymentId());
    if (!children.isEmpty()) {
        // 割り勘(totalSplits > 0)の場合は親会計を表示
        // 個別会計(totalSplits = null)の場合は親会計を非表示
        Integer totalSplits = p.getTotalSplits();
        return totalSplits != null && totalSplits > 0;
    }
    // 子会計が存在しない通常の会計は表示
    return true;
}

// 親会計が存在する場合（子会計）
// 割り勘(totalSplits > 0)の場合は子会計を非表示
// 個別会計(totalSplits = null)の場合は子会計を表示
Integer totalSplits = p.getTotalSplits();
return totalSplits == null || totalSplits == 0;
```

### テスト

`PaymentHistoryFilterTest.java` で以下のシナリオをテスト:
- 通常会計の表示確認
- 割り勘会計での親会計のみ表示、子会計非表示の確認
- 個別会計での子会計のみ表示、親会計非表示の確認
- 複数の会計タイプが混在する場合の正しいフィルタリング確認

## 今後の拡張案

1. **支払い済み商品の視覚化**: 個別会計モーダルで既に支払った商品をグレーアウト
2. **会計の取り消し**: 部分会計の取り消し機能
3. **レシート印刷**: 各分割会計ごとのレシート発行
4. **統計レポート**: 割り勘・個別会計の利用状況分析

## トラブルシューティング

### よくある問題

1. **会計が完了しない**
   - 全ての商品または全ての人数分の会計が完了しているか確認
   - データベースで payment_status を確認

2. **退店時刻が記録されない**
   - 最後の会計が completed になっているか確認
   - Visit テーブルの leave_time を確認

3. **金額が合わない**
   - 割り勘の場合、端数は最後に含まれることを確認
   - 個別会計の場合、商品の割引が正しく適用されているか確認

## 参考資料

- `PaymentSplitService.java`: ビジネスロジックの実装
- `PaymentController.java`: API エンドポイントの実装
- `payment.html`: フロントエンド UI の実装
- `individual_payment_schema.sql`: データベース変更スクリプト
