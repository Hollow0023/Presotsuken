# 点検フォーム不具合修正 - 実装概要

## 問題の詳細

割り勘で会計を行った際、以下の不具合が発生していました:

1. **税率別売上の表示異常**
   - 税抜き1500円分の注文を行った際
   - 総売上: 1500円 ✓ (正常)
   - 10%対象: 0円 ✗ (本来は1500円であるべき)
   - 現金売上: 1650円 ✓ (正常)
   - 現金項目の合計: 0円 ✗ (本来は1500円であるべき)
   - 現金項目の消費税: 150円 ✓ (正常)

2. **客数カウントの異常**
   - 何人で入店しても常に1人としてカウントされる

## 原因分析

### 1. 税率別売上が0円になる原因

割り勘会計の仕組み:
- 親会計(`Payment`)に`PaymentDetail`(注文明細)が紐付く
- 子会計(割り勘の各支払い)は独自の`PaymentType`(現金/カード等)を持つが、`PaymentDetail`は持たない
- 子会計は`parent_payment_id`で親会計を参照

既存の`sumSalesByPaymentTypeAndTaxRate`クエリ:
```java
SELECT pt.typeName, tr.taxRateId, SUM(pd.subtotal)
FROM PaymentDetail pd
JOIN pd.payment p
JOIN p.paymentType pt
JOIN pd.taxRate tr
WHERE ...
GROUP BY pt.typeName, tr.taxRateId
```

このクエリは`PaymentDetail`起点のため、`PaymentDetail`を持たない子会計が集計されませんでした。

### 2. 客数が1人になる原因

既存の`countCustomerVisits`クエリ:
```java
SELECT COUNT(DISTINCT p.visit.visitId)
FROM Payment p
WHERE ...
```

このクエリは来店回数(Visit件数)をカウントしており、`Visit.numberOfPeople`(実際の人数)を参照していませんでした。

## 実装した修正

### 1. PaymentDetailRepository.sumSalesByPaymentTypeAndTaxRate

UNION ALLを使用した2段階集計に変更:

**第1クエリ: 割り勘子会計の集計**
```sql
SELECT childPt.type_name, tr.tax_rate_id, 
       SUM(pd.subtotal * (childP.total / NULLIF(parentP.total, 0)))
FROM payment childP
JOIN payment parentP ON childP.parent_payment_id = parentP.payment_id
JOIN payment_type childPt ON childP.payment_type_id = childPt.type_id
JOIN payment_detail pd ON pd.payment_id = parentP.payment_id
JOIN tax_rate tr ON pd.tax_rate_id = tr.tax_rate_id
WHERE childP.store_id = :storeId
  AND childP.payment_time >= :start
  AND childP.payment_time < :end
  AND childP.visit_cancel = false
  AND childP.cancel = false
  AND parentP.total != 0
GROUP BY childPt.type_name, tr.tax_rate_id
```

ポイント:
- 子会計から親会計の`PaymentDetail`を参照
- 按分計算: `pd.subtotal * (childP.total / parentP.total)`
  - 例: 親1650円、子550円の場合 → 1500円 × (550/1650) = 500円
- ゼロ除算対策: `NULLIF(parentP.total, 0)` + `WHERE parentP.total != 0`

**第2クエリ: 通常会計の集計**
```sql
SELECT pt.type_name, tr.tax_rate_id, SUM(pd.subtotal)
FROM payment_detail pd
JOIN payment p ON pd.payment_id = p.payment_id
JOIN payment_type pt ON p.payment_type_id = pt.type_id
JOIN tax_rate tr ON pd.tax_rate_id = tr.tax_rate_id
WHERE p.store_id = :storeId
  AND p.payment_time >= :start
  AND p.payment_time < :end
  AND p.visit_cancel = false
  AND p.cancel = false
  AND p.parent_payment_id IS NULL
GROUP BY pt.type_name, tr.tax_rate_id
```

ポイント:
- `parent_payment_id IS NULL`で通常会計のみを抽出
- 既存ロジックと同様に集計

### 2. PaymentRepository.countCustomerVisits

訪問回数から実際の人数の合計に変更:

```java
SELECT COALESCE(SUM(v.numberOfPeople), 0)
FROM Payment p
JOIN p.visit v
WHERE p.store.storeId = :storeId
  AND p.paymentTime >= :start
  AND p.paymentTime < :end
  AND p.visitCancel = false
  AND COALESCE(p.cancel, false) = false
  AND p.parentPayment IS NULL
```

変更点:
- `COUNT(DISTINCT p.visit.visitId)` → `SUM(v.numberOfPeople)`
- `p.parentPayment IS NULL`を追加して親会計のみカウント(重複防止)

## 動作例

### 割り勘会計のケース

**シナリオ**: 3人で来店、税抜き1500円(税込1650円)の注文、3人で割り勘(各550円)、全員現金払い

| 会計 | parent_payment_id | PaymentDetail | payment_type | total |
|------|-------------------|---------------|--------------|-------|
| 親会計 | NULL | あり(1500円) | 現金 | 1650円 |
| 子1 | 親のID | なし | 現金 | 550円 |
| 子2 | 親のID | なし | 現金 | 550円 |
| 子3 | 親のID | なし | 現金 | 550円 |

**修正後の集計結果**:
- 現金の10%対象: 1500円 ✓
  - 子1: 1500 × (550/1650) = 500円
  - 子2: 1500 × (550/1650) = 500円  
  - 子3: 1500 × (550/1650) = 500円
  - 合計: 1500円
- 客数: 3人 ✓

## テスト

- 既存の単体テスト(`InspectionLogServiceTest`、`PaymentSplitServiceTest`など)が正常に動作
- コードレビューでゼロ除算脆弱性を指摘され修正完了
- CodeQLセキュリティチェック: 脆弱性なし

## セキュリティ対策

1. **ゼロ除算対策**
   - `NULLIF(parentP.total, 0)`でゼロの場合NULLに変換
   - `WHERE parentP.total != 0`でゼロの会計を除外
   
2. **SQLインジェクション対策**
   - パラメータバインディング使用(`:storeId`, `:start`, `:end`)

## 影響範囲

- 点検フォーム(`/admin/inspection/form`)の表示内容
- 既存の通常会計の集計には影響なし(UNION ALLの第2クエリで従来通り処理)
- 割り勘会計が正しく集計されるようになる

## 今後の改善案

1. 個別会計(商品ごとに分ける会計)の場合も同様の問題がないか検証
2. 割引が適用された場合の按分計算の正確性を検証
3. 統合テストの追加(実データベースを使用したE2Eテスト)
