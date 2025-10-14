# 個別会計機能 実装サマリー

## 実装概要

PreSotsuken 飲食店向け注文管理システムに、個別会計機能（割り勘会計・商品別会計）を実装しました。

## 実装した機能

### 1. 割り勘会計
- 合計金額を指定人数で均等分割
- 端数処理: 余りは最後の会計に含める
- 例: 3,300円 ÷ 3人 = 1,100円 × 2人 + 1,100円 (最後)

### 2. 個別会計（商品選択会計）
- 商品単位でチェックボックス選択
- 選択商品のみを会計
- 商品ごとの割引適用可能

### 3. 部分会計の管理
- 全ての会計が完了するまで Visit の退店時刻を記録しない
- 部分完了状態 (PARTIAL) の管理
- 会計の再開機能

## ファイル構成

### バックエンド

#### エンティティ (Entity)
- **Payment.java** - 親会計参照、ステータス、分割情報のフィールドを追加
- **PaymentDetail.java** - 支払い済み会計への参照を追加

#### DTO (Data Transfer Object)
- **SplitPaymentRequest.java** - 割り勘会計リクエスト
- **IndividualPaymentRequest.java** - 個別会計リクエスト
- **RemainingPaymentDto.java** - 残り会計情報レスポンス

#### サービス (Service)
- **PaymentSplitService.java** - 個別会計のビジネスロジック
  - `processSplitPayment()` - 割り勘会計処理
  - `processIndividualPayment()` - 個別会計処理
  - `getRemainingPayment()` - 残り会計情報取得

#### コントローラー (Controller)
- **PaymentController.java** - エンドポイント追加
  - `POST /payments/split` - 割り勘会計
  - `POST /payments/individual` - 個別会計
  - `GET /payments/{paymentId}/remaining` - 残り会計情報

#### テスト (Test)
- **PaymentSplitServiceTest.java** - 単体テスト (7つのテストケース)
  - 割り勘計算テスト
  - 余り処理テスト
  - 個別会計テスト
  - 完了判定テスト
  - 重複支払いチェック

### フロントエンド

#### HTML/JavaScript
- **payment.html** - UI追加
  - 割り勘会計モーダル
  - 個別会計モーダル
  - JavaScript関数:
    - `openSplitPaymentModal()` - 割り勘モーダル表示
    - `processSplitPayment()` - 割り勘会計実行
    - `openIndividualPaymentModal()` - 個別会計モーダル表示
    - `processIndividualPayment()` - 個別会計実行
    - `updateSplitPreview()` - 割り勘プレビュー更新
    - `updateIndividualTotal()` - 個別会計合計更新

### データベース

#### スキーマ変更スクリプト
- **individual_payment_schema.sql** - マイグレーションスクリプト

#### 追加カラム

**payment テーブル:**
| カラム名 | 型 | 説明 |
|---------|-----|------|
| parent_payment_id | INT | 元の会計ID |
| payment_status | VARCHAR(20) | PENDING/PARTIAL/COMPLETED |
| split_number | INT | 分割番号 |
| total_splits | INT | 総分割数 |

**payment_detail テーブル:**
| カラム名 | 型 | 説明 |
|---------|-----|------|
| paid_in_payment_id | INT | 支払い済み会計ID |

### ドキュメント
- **INDIVIDUAL_PAYMENT_GUIDE.md** - 実装ガイド (API仕様、使用方法、トラブルシューティング)

## テスト結果

✅ 全テスト成功 (7/7)
- 割り勘会計_3人で割る_1人目 ✅
- 割り勘会計_3人で割る_3人目_余りを含める ✅
- 割り勘会計_余りが出る場合_最後に含める ✅
- 個別会計_一部の商品を支払い ✅
- 個別会計_全商品支払い後に完了状態になる ✅
- 残り会計情報取得_未払い商品あり ✅
- 既に支払い済みの商品を再度支払おうとするとエラー ✅

## データフロー

### 割り勘会計のフロー

```
1. ユーザーが「割り勘会計」ボタンをクリック
   ↓
2. モーダルで分割人数と現在の順番を選択
   ↓
3. フロントエンドが POST /payments/split を呼び出し
   ↓
4. PaymentSplitService.processSplitPayment() が実行
   ↓
5. 元の会計を PARTIAL 状態に更新 (初回のみ)
   ↓
6. 子会計レコードを作成 (parent_payment_id を設定)
   ↓
7. 最後の会計の場合:
   - 元の会計を COMPLETED に更新
   - Visit.leaveTime を記録
   ↓
8. レスポンスを返す (success, amount, completed)
```

### 個別会計のフロー

```
1. ユーザーが「個別会計」ボタンをクリック
   ↓
2. モーダルで支払う商品を選択
   ↓
3. フロントエンドが POST /payments/individual を呼び出し
   ↓
4. PaymentSplitService.processIndividualPayment() が実行
   ↓
5. 選択商品が未払いかチェック
   ↓
6. 子会計レコードを作成
   ↓
7. PaymentDetail.paid_in_payment_id を更新 (支払い済みマーク)
   ↓
8. 全商品が支払い済みかチェック
   ↓
9. 全て支払い済みの場合:
   - 元の会計を COMPLETED に更新
   - Visit.leaveTime を記録
   ↓
10. レスポンスを返す (success, amount, completed)
```

## 使用技術

- **Java 21**
- **Spring Boot 3.2.5**
- **JPA/Hibernate**
- **Lombok**
- **JUnit 5 & Mockito** (テスト)
- **Thymeleaf** (テンプレートエンジン)
- **JavaScript (Vanilla)** (フロントエンド)

## デプロイ手順

1. **データベースマイグレーション**
   ```bash
   mysql -u [ユーザー名] -p [データベース名] < PreSotuken/individual_payment_schema.sql
   ```

2. **アプリケーションビルド**
   ```bash
   cd PreSotuken
   ./gradlew clean build -x test
   ```

3. **アプリケーション起動**
   ```bash
   java -jar build/libs/PreSotuken-0.0.1-SNAPSHOT.jar
   ```

## 動作確認方法

1. アプリケーションを起動
2. ブラウザで会計画面を開く (`/payments?visitId=X`)
3. 「割り勘会計」ボタンをクリックして動作確認
4. 「個別会計」ボタンをクリックして動作確認

## 今後の拡張候補

1. **会計履歴の表示改善**
   - 親子会計の関係を視覚化
   - 分割会計の履歴を見やすく表示

2. **支払い済み商品の表示**
   - 個別会計モーダルで既に支払った商品をグレーアウト

3. **会計の取り消し機能**
   - 部分会計の取り消し
   - 全体の取り消しと再開

4. **レシート印刷対応**
   - 各分割会計ごとの領収書発行

5. **統計レポート**
   - 割り勘・個別会計の利用状況分析

## トラブルシューティング

### 問題: 会計が完了しない
**原因:** 全ての商品/人数分の会計が完了していない  
**解決:** データベースで payment_status を確認

### 問題: 退店時刻が記録されない
**原因:** 最後の会計が COMPLETED になっていない  
**解決:** Visit テーブルの leave_time と Payment の payment_status を確認

### 問題: 金額が合わない
**原因:** 端数処理の理解不足  
**解決:** 割り勘の場合、端数は最後の会計に含まれることを確認

## まとめ

個別会計機能の実装により、以下が可能になりました:

✅ 複数人での公平な割り勘会計  
✅ 商品単位での柔軟な支払い  
✅ 部分会計と会計の再開  
✅ 全会計完了後の退店時刻記録  

全てのテストが成功し、実装は完了しています。
