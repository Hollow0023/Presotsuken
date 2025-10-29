# 割り勘会計の売上二重計上問題 修正レポート

## 問題の概要

点検フォームで割り勘会計を行った際の売上が正しく反映されない問題が確認されました。
具体的には、売上が実際の金額の約2倍に計上されていました。

## 問題の詳細

### 現象
3,300円の会計を3人で割り勘した場合：
- 期待される集計額: 3,300円
- 実際の集計額: 6,600円（二重計上）

### 原因
`PaymentRepository.sumCashSales()`クエリが、親会計と子会計の両方を集計していたため。

割り勘会計のデータ構造：
- 親会計（parent_payment_id = NULL）: 3,300円（全子会計の合計を集約）
- 子会計1（parent_payment_id = 親のID）: 1,100円
- 子会計2（parent_payment_id = 親のID）: 1,100円
- 子会計3（parent_payment_id = 親のID）: 1,100円

従来のクエリは、これら4件すべてを集計していました：
```
3,300 + 1,100 + 1,100 + 1,100 = 6,600円（誤り）
```

## 修正内容

### 変更ファイル
- `PreSotuken/src/main/java/com/order/repository/PaymentRepository.java`
- `PreSotuken/src/test/java/com/order/service/InspectionLogServiceTest.java`

### 修正方法
`sumCashSales()`クエリに以下の条件を追加：

```sql
AND NOT EXISTS (
    SELECT 1 FROM Payment child
    WHERE child.parentPayment.paymentId = p.paymentId
)
```

この条件により：
- 子会計を持つ親会計は集計から除外される
- 子会計のみが集計される（1,100 + 1,100 + 1,100 = 3,300円）
- 通常の会計（分割されていない）は引き続き正しく集計される

### 修正後のクエリ（全体）

```java
@Query("""
    SELECT SUM(p.total)
    FROM Payment p
    WHERE p.store.storeId = :storeId
      AND p.paymentTime BETWEEN :start AND :end
      AND p.visitCancel = false
      AND COALESCE(p.cancel, false) = false
      AND p.paymentType.isInspectionTarget = true
      AND NOT EXISTS (
          SELECT 1 FROM Payment child
          WHERE child.parentPayment.paymentId = p.paymentId
      )
""")
BigDecimal sumCashSales(
    @Param("storeId") Integer storeId,
    @Param("start") LocalDateTime start,
    @Param("end") LocalDateTime end
);
```

## テスト

### 追加テスト
`InspectionLogServiceTest.testBuildInspectionSummary_WithSplitPayments_CountsChildPaymentsOnly()`

このテストは、割り勘会計が行われた場合に：
1. 子会計のみが集計されること
2. 正しい売上金額（3,300円）が反映されること
を検証します。

### テスト結果
- 新規テスト: PASS ✓
- 既存の全テスト: PASS ✓

## 影響範囲の分析

### 影響を受ける機能
✓ **点検フォーム** - 現金売上の集計が正しくなる

### 影響を受けない機能
以下の機能は正常に動作し続けます：

✓ **個別会計** - PaymentDetailレコードが適切に分割されるため、重複計上なし
✓ **来店数カウント** - `COUNT(DISTINCT visit_id)`を使用しているため問題なし
✓ **税率別売上** - PaymentDetailベースのクエリのため問題なし
✓ **会計履歴表示** - フィルタリングロジックは変更なし

## 今後の考慮事項

### 他のクエリの確認
以下のクエリは問題ないことを確認済み：

1. **`PaymentRepository.countCustomerVisits()`**
   - `COUNT(DISTINCT p.visit.visitId)`を使用
   - 親・子会計が複数あっても1訪問として正しくカウント

2. **PaymentDetailベースのクエリ**
   - 割り勘会計: PaymentDetailは親に紐付いたまま（重複なし）
   - 個別会計: PaymentDetailは適切に分割される（重複なし）

### ドキュメント参照
割り勘・個別会計の仕様詳細は以下を参照：
- `INDIVIDUAL_PAYMENT_GUIDE.md` - 個別会計機能の実装ガイド
- `PAYMENT_HISTORY_FILTER_SUMMARY.md` - 会計履歴の表示ルール

## セキュリティ

CodeQL による静的解析を実行し、セキュリティ上の問題がないことを確認しました。

## まとめ

この修正により、割り勘会計を行った際の点検フォームでの売上が正しく反映されるようになりました。
- ✓ 二重計上の問題を解決
- ✓ 既存機能への影響なし
- ✓ テストカバレッジの向上
- ✓ セキュリティ問題なし
