# GitHub Copilot カスタム指示 - PreSotuken

## プロジェクト概要
PreSotuken（プレソツケン）は日本の飲食店向け注文管理システムです。Spring Boot + Java 21で構築されています。

## 重要な開発原則

### 1. 言語使用
- **コメント**: 日本語でビジネスロジックを説明
- **変数・メソッド名**: 英語を使用
- **ドメイン知識**: 飲食店の業務フローを理解して実装

### 2. アーキテクチャ
- Controller-Service-Repository パターン
- JPA + Lombok + Spring Boot の組み合わせ
- WebSocket でリアルタイム座席状態更新

### 3. 業務ドメイン
- **座席管理**: 空席 → 使用中 → 会計待ち の状態遷移
- **注文処理**: メニュー選択 → 調理指示 → 提供 → 会計
- **印刷機能**: ESC/POS コマンドでレシート・注文票印刷

### 4. コーディングスタイル
```java
// 良い例: 日本語コメント + 英語メソッド名
/**
 * 座席の状態を更新し、WebSocketで通知します
 */
@Transactional
public void updateSeatStatus(Integer seatId, SeatStatus status) {
    // 座席情報を取得
    Seat seat = findSeatById(seatId);
    seat.setStatus(status);
    // WebSocketで状態変更を通知
    notifyStatusChange(seat);
}
```

### 5. よく使用する依存関係
- `@RequiredArgsConstructor` - Lombokによる依存性注入
- `@Transactional` - データベース操作のトランザクション管理
- `SimpMessagingTemplate` - WebSocket メッセージ送信
- `@Entity`, `@Table` - JPA エンティティ定義

## 注意事項
- 座席状態の変更時は必ずWebSocketで通知
- 会計処理は税率計算（8%/10%）に注意
- 印刷機能はJSON形式でESC/POSコマンドを生成
- エラーハンドリングでは日本語メッセージを表示