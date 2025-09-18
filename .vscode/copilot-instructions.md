# VS Code Copilot 指示 - PreSotuken プロジェクト

このプロジェクトは日本の飲食店向け注文管理システム「PreSotuken」です。

## 技術スタック
- Java 21 + Spring Boot 3.2.5
- JPA, Thymeleaf, WebSocket
- MySQL/H2, Gradle, Lombok

## 開発ガイドライン

### コメント・命名・Git
- コメント: 日本語でビジネスロジックを説明
- コード: 英語で命名（変数、メソッド、クラス）
- **コミット・PR**: 日本語でメッセージ・説明を記述

### アーキテクチャ
```
Controller → Service → Repository → Entity
```

### 業務ドメイン
- 座席管理（空席→使用中→会計待ち）
- 注文処理（注文→調理→提供→会計）
- 印刷機能（ESC/POS）

### 必須パターン
```java
@Service
@RequiredArgsConstructor
@Transactional
public class ExampleService {
    private final ExampleRepository repository;
    private final SimpMessagingTemplate messagingTemplate;
}
```

詳細は `.copilot-instructions.md` を参照してください。