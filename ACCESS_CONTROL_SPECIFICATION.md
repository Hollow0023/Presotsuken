# PreSotuken アクセス制限仕様書

## 概要
PreSotsukenシステムには、レジ端末とテーブル端末の2種類の端末があり、それぞれ異なるアクセス制御方式を採用しています。

## 1. レジ端末（管理者端末）のアクセス制限

### 1.1 認証方式
- **エンドポイント**: `/login`
- **必要な情報**:
  - 店舗ID (`storeId`)
  - 店舗名 (`storeName`)
  - クライアントIPアドレス（自動取得）

### 1.2 認証フロー
1. ユーザーが店舗IDと店舗名を入力
2. システムがDBで店舗情報を検証 (`StoreRepository.existsByStoreIdAndStoreName()`)
3. クライアントのIPアドレスを取得（X-Forwarded-Forヘッダー対応）
4. IPアドレスと店舗IDでTerminalテーブルを検索 (`TerminalRepository.findByIpAddressAndStore_StoreId()`)
5. 端末が見つからない場合、`/login?error=terminalNotFound` にリダイレクト
6. 認証成功時、以下の情報をCookieに保存（有効期限: 180日）:
   - `storeId`: 店舗ID
   - `terminalId`: 端末ID
   - `adminFlag`: 管理者フラグ（"true"/"false"）
   - `storeName`: 店舗名
   - `seatId`: 座席ID（管理者端末でない場合のみ）

### 1.3 IP制限の実装
- **実装箇所**: `LoginController.login()` メソッド
- **IPアドレス取得ロジック**:
  ```java
  private String getClientIp(HttpServletRequest request) {
      String xfHeader = request.getHeader("X-Forwarded-For");
      String ip = (xfHeader == null) ? request.getRemoteAddr() : xfHeader.split(",")[0];
      
      // IPv6のループバックアドレスをIPv4形式に変換
      if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
          ip = "127.0.0.1";
      }
      
      return ip;
  }
  ```
- **制限方法**: 
  - Terminalエンティティに登録されたIPアドレスからのみアクセス可能
  - IPアドレスが未登録の端末からはログイン不可

### 1.4 遷移先
- **管理者端末** (`isAdmin == true`): `/seats` （座席管理画面）
- **一般端末** (`isAdmin == false`): `/visits/orderwait` （注文待機画面）

## 2. テーブル端末のアクセス制限

### 2.1 認証方式
- **エンドポイント**: `/tableLogin`
- **必要な情報**:
  - 店舗ID (`storeId`)
  - 店舗名 (`storeName`)

### 2.2 認証フロー
1. ユーザーが店舗IDと店舗名を入力
2. システムがDBで店舗情報を検証 (`StoreRepository.findById()` + 店舗名照合)
3. 認証成功時、以下の情報をCookieに保存（有効期限: 120日）:
   - `storeId`: 店舗ID
   - `storeName`: 店舗名
4. `/visits/orderwait` にリダイレクト

### 2.3 IP制限の実装
- **実装箇所**: `VisitController.orderWaitPage()` メソッド
- **IPアドレス取得ロジック**:
  ```java
  String ip = request.getRemoteAddr();
  if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
      ip = "127.0.0.1";
  }
  ```
- **制限方法**:
  - `/visits/orderwait` アクセス時に、CookieのstoreIDとIPアドレスでTerminalテーブルを検索
  - 登録されていない端末の場合、`RuntimeException("端末が見つかりません")` が発生
  - 例外発生時はエラーページに遷移

### 2.4 自動ログイン機能
- Cookieに `storeId` と `storeName` が保存されている場合、自動的にログイン処理を実行
- 店舗情報が一致すれば、ログイン画面をスキップして `/visits/orderwait` に遷移

## 3. 共通のアクセス制御機能

### 3.1 ログインチェックインターセプター
- **実装クラス**: `LoginCheckInterceptor`
- **適用範囲**: 全てのパス (`/**`)
- **除外パス**:
  - `/` (ルート)
  - `/login`
  - `/logout`
  - `/css/**`
  - `/js/**`
  - `/images/**`
  - `/favicon.ico`
  - `/fonts/**`
- **チェック内容**: Cookieに `storeId` が存在するか確認
- **未ログイン時の動作**: `/login` にリダイレクト

### 3.2 管理者ページアクセスインターセプター
- **実装クラス**: `AdminPageInterceptor`
- **適用範囲**: `/admin/**` パス
- **チェック内容**: Cookieの `adminFlag` が "true" であるか確認
- **権限不足時の動作**: `/login?admin=denied` にリダイレクト

### 3.3 WebMVC設定
- **実装クラス**: `WebConfig`
- **インターセプター登録**:
  1. `LoginCheckInterceptor`: 全パスに適用
  2. `AdminPageInterceptor`: `/admin/**` に適用

## 4. Terminalエンティティ

### 4.1 テーブル構造
```java
@Entity
public class Terminal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer terminalId;        // 端末ID
    
    @OneToOne
    @JoinColumn(name = "seat_id")
    private Seat seat;                 // 関連座席
    
    @ManyToOne
    @JoinColumn(name = "store_id")
    private Store store;               // 所属店舗
    
    private String ipAddress;          // IPアドレス
    private boolean isAdmin;           // 管理者フラグ
}
```

### 4.2 重要なリポジトリメソッド
```java
// IPアドレスと店舗IDで端末を検索
Optional<Terminal> findByIpAddressAndStore_StoreId(String ipAddress, Integer storeId);

// 店舗IDで全端末を検索
List<Terminal> findByStoreStoreId(Integer storeId);
```

## 5. セキュリティ上の注意点

### 5.1 現在の実装の特徴
- **パスワード認証なし**: 店舗名と店舗IDの組み合わせのみで認証（テーブル端末）
- **IP制限による保護**: レジ端末は登録済みIPアドレスからのみアクセス可能
- **Cookie認証**: セッションベースではなくCookie（最大180日）で状態管理
- **プロキシ対応**: X-Forwarded-Forヘッダーに対応（リバースプロキシ環境対応）

### 5.2 セキュリティリスクと対策
| リスク | 現状 | 推奨対策 |
|--------|------|----------|
| Cookie盗聴 | HTTP通信で暗号化なし | HTTPS（TLS/SSL）の導入 |
| セッション固定攻撃 | Cookieの有効期限が長い | セッションIDの再生成、有効期限の短縮 |
| IPスプーフィング | X-Forwarded-Forヘッダーを信頼 | 信頼できるプロキシのみ許可する設定 |
| 店舗情報の推測 | 店舗IDが連番の可能性 | ランダムなトークンやUUIDの使用 |
| 管理者権限の昇格 | Cookieの改ざんで権限変更可能 | 署名付きCookieの使用、サーバー側での権限再検証 |

## 6. 端末登録の流れ

### 6.1 管理画面での端末登録
- **画面**: `/admin/terminals` (admin_terminals.html)
- **必要情報**:
  - 座席ID（管理者端末の場合は不要）
  - IPアドレス
  - 管理者フラグ

### 6.2 登録時の処理
- **サービス**: `TerminalService.createTerminal()`
- **バリデーション**:
  - IPアドレスが空でないこと
  - 座席が存在すること（非管理者端末の場合）
  - 店舗が存在すること

## 7. 実装ファイル一覧

### 7.1 コントローラー
- `LoginController.java`: レジ端末ログイン処理
- `TableLoginController.java`: テーブル端末ログイン処理
- `VisitController.java`: テーブル端末のIP制限チェック
- `AdminTerminalController.java`: 端末管理API

### 7.2 インターセプター
- `LoginCheckInterceptor.java`: ログイン状態チェック
- `AdminPageInterceptor.java`: 管理者権限チェック

### 7.3 設定
- `WebConfig.java`: インターセプター登録

### 7.4 エンティティ・リポジトリ
- `Terminal.java`: 端末エンティティ
- `TerminalRepository.java`: 端末データアクセス

### 7.5 サービス
- `TerminalService.java`: 端末管理ロジック

## 8. テスト環境への配慮

### 8.1 ローカルホスト対応
- IPv6ループバックアドレス (`::1`, `0:0:0:0:0:0:0:1`) を自動的に `127.0.0.1` に変換
- 開発環境でのテストを容易にするための措置

### 8.2 実装箇所
- `LoginController.getClientIp()`: レジ端末ログイン時
- `VisitController.orderWaitPage()`: テーブル端末アクセス時

## まとめ

PreSotsukenのアクセス制限システムは、以下の3層構造で実装されています：

1. **認証層**: 店舗情報による初期認証（店舗ID + 店舗名）
2. **端末制限層**: IPアドレスベースの端末登録制御
3. **権限管理層**: Cookie + インターセプターによる権限チェック

この仕組みにより、登録済み端末からのみシステムへのアクセスを許可し、さらに管理者機能へのアクセスは管理者フラグによって制御されています。
