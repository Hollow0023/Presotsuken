# PreSotuken 機能別クラスファイル一覧

このドキュメントは、PreSotukenシステムの実装済み機能と、それに関連するクラスファイルの対応関係を示します。

## 目次
- [基本機能](#基本機能)
- [メニュー関連](#メニュー関連)
- [事務操作](#事務操作)
- [店舗設定](#店舗設定)

---

## 基本機能

### 1. 店舗登録
**概要**: 新しい店舗をシステムに登録する機能

**関連クラス**:
- **Controller**: `StoreRegistrationController.java`
- **Service**: `StoreService.java`
- **Entity**: `Store.java`
- **Repository**: `StoreRepository.java`

---

### 2. ログイン
**概要**: ユーザー認証とログイン処理

**関連クラス**:
- **Controller**: 
  - `LoginController.java`
  - `LogoutController.java`
  - `TableLoginController.java` (客席用ログイン)
- **Service**: 
  - `UserService.java`
  - `CookieUtil.java`
- **Entity**: `User.java`
- **Repository**: `UserRepository.java`
- **Interceptor**: 
  - `LoginCheckInterceptor.java`
  - `AdminPageInterceptor.java`
- **Util**: `CookieUtil.java` (util)

---

### 3. 入店
**概要**: 顧客の来店と座席への案内を管理

**関連クラス**:
- **Controller**: 
  - `VisitController.java`
  - `VisitInfoController.java`
  - `SeatController.java`
- **Service**: `SeatService.java`
- **Entity**: 
  - `Visit.java`
  - `Seat.java`
- **Repository**: 
  - `VisitRepository.java`
  - `SeatRepository.java`
- **DTO**: `SeatRequestDto.java`

---

### 4. 注文
**概要**: メニュー注文の登録と管理

**関連クラス**:
- **Controller**: 
  - `OrderController.java`
  - `MenuController.java`
- **Service**: 
  - `OrderService.java`
  - `MenuService.java`
- **Entity**: 
  - `Menu.java`
  - `MenuOption.java`
  - `Payment.java` (注文は会計に紐づく)
  - `PaymentDetail.java`
- **Repository**: 
  - `MenuRepository.java`
  - `PaymentRepository.java`
  - `PaymentDetailRepository.java`
- **DTO**: 
  - `OrderHistoryDto.java`
  - `MenuWithOptionsDTO.java`

---

### 5. 会計・会計中止
**概要**: 精算処理と会計のキャンセル

**関連クラス**:
- **Controller**: `PaymentController.java`
- **Service**: 
  - `PaymentService.java`
  - `PaymentSplitService.java` (分割会計)
  - `PaymentLookupService.java` (会計情報取得)
- **Entity**: 
  - `Payment.java`
  - `PaymentDetail.java`
  - `PaymentDetailOption.java`
- **Repository**: 
  - `PaymentRepository.java`
  - `PaymentDetailRepository.java`
  - `PaymentDetailOptionRepository.java`
- **DTO**: 
  - `PaymentSummaryDto.java`
  - `PaymentFinalizeRequest.java`
  - `SplitPaymentRequest.java`
  - `IndividualPaymentRequest.java`
  - `RemainingPaymentDto.java`
  - `PaymentHistoryUpdateRequest.java`

---

### 6. 店員呼び出し
**概要**: 顧客からの呼び出し通知機能

**関連クラス**:
- **Controller**: `CallReceiveController.java`
- **Config**: `WebSocketConfig.java` (WebSocket通信設定)

---

### 7. 注文履歴閲覧
**概要**: 過去の注文履歴の参照

**関連クラス**:
- **Controller**: `OrderController.java`
- **Service**: `OrderService.java`
- **Entity**: 
  - `Payment.java`
  - `PaymentDetail.java`
- **Repository**: 
  - `PaymentRepository.java`
  - `PaymentDetailRepository.java`
- **DTO**: `OrderHistoryDto.java`

---

### 8. 担当者切り替え（TOP）
**概要**: 担当スタッフの変更機能

**関連クラス**:
- **Controller**: `UserController.java`
- **Service**: `UserService.java`
- **Entity**: `User.java`
- **Repository**: `UserRepository.java`

---

### 9. 飲み放題等プラン
**概要**: 時間制プラン（飲み放題等）の管理

**関連クラス**:
- **Controller**: `AdminPlanController.java`
- **Service**: `PlanService.java`
- **Entity**: 
  - `Plan.java`
  - `PlanMenuGroupMap.java`
  - `PlanMenuGroupMapId.java`
- **Repository**: 
  - `PlanRepository.java`
  - `PlanMenuGroupMapRepository.java`
- **DTO**: 
  - `PlanRequestDto.java`
  - `PlanResponseDto.java`

---

### 10. 注文伝票印刷
**概要**: 注文伝票やレシートの印刷機能

**関連クラス**:
- **Controller**: 
  - `ReceiptController.java`
  - `PrintTestController.java`
- **Service**: 
  - `PrintService.java`
  - `ReceiptService.java`
  - `print/PrintCommandService.java`
  - `print/PrintFormatService.java`
- **Entity**: 
  - `Receipt.java`
  - `PrinterConfig.java`
- **Repository**: 
  - `ReceiptRepository.java`
  - `PrinterConfigRepository.java`
- **DTO**: 
  - `ReceiptIssueRequest.java`
  - `ReceiptResponseDto.java`

---

## メニュー関連

### 11. メニュー設定
**概要**: メニュー項目の追加・編集・削除

**関連クラス**:
- **Controller**: `MenuController.java`
- **Service**: 
  - `MenuService.java`
  - `MenuAddService.java`
- **Entity**: 
  - `Menu.java`
  - `MenuOption.java`
  - `MenuPrinterMap.java`
- **Repository**: 
  - `MenuRepository.java`
  - `MenuOptionRepository.java`
  - `MenuPrinterMapRepository.java`
- **DTO**: 
  - `MenuForm.java`
  - `MenuWithOptionsDTO.java`

---

### 12. メニューカテゴリ設定
**概要**: メニューグループ（カテゴリ）の管理

**関連クラス**:
- **Controller**: `MenuGroupController.java`
- **Service**: `MenuGroupService.java`
- **Entity**: `MenuGroup.java`
- **Repository**: `MenuGroupRepository.java`

---

### 13. オプション設定
**概要**: メニューに付与するオプション（トッピング等）の管理

**関連クラス**:
- **Controller**: `OptionManagementController.java`
- **Service**: `OptionManagementService.java`
- **Entity**: 
  - `OptionGroup.java`
  - `OptionItem.java`
  - `MenuOption.java`
- **Repository**: 
  - `OptionGroupRepository.java`
  - `OptionItemRepository.java`
  - `MenuOptionRepository.java`
- **DTO**: 
  - `OptionGroupDTO.java`
  - `OptionItemDTO.java`
  - `OptionDeletionCheckDTO.java`

---

### 14. メニュー品切れ管理
**概要**: メニューの提供可否（売り切れ）状態の管理

**関連クラス**:
- **Controller**: `MenuSoldOutController.java`
- **Service**: `MenuService.java`
- **Entity**: `Menu.java` (isSoldOut フラグ)
- **Repository**: `MenuRepository.java`
- **DTO**: 
  - `SoldOutStatusRequest.java`
  - `BulkSoldOutStatusRequest.java`

---

## 事務操作

### 15. 入出金
**概要**: 現金の入出金（つり銭補充等）の記録

**関連クラス**:
- **Controller**: `CashTransactionController.java`
- **Service**: `CashTransactionService.java`
- **Entity**: `CashTransaction.java`
- **Repository**: `CashTransactionRepository.java`
- **DTO**: `CashTransactionRequest.java`

---

### 16. 入出金履歴
**概要**: 過去の現金入出金履歴の確認

**関連クラス**:
- **Controller**: `CashTransactionController.java`
- **Service**: `CashTransactionService.java`
- **Entity**: `CashTransaction.java`
- **Repository**: `CashTransactionRepository.java`

---

### 17. 会計履歴
**概要**: 過去の会計記録の参照

**関連クラス**:
- **Controller**: `PaymentController.java`
- **Service**: `PaymentService.java`
- **Entity**: 
  - `Payment.java`
  - `PaymentDetail.java`
- **Repository**: 
  - `PaymentRepository.java`
  - `PaymentDetailRepository.java`
- **DTO**: `PaymentSummaryDto.java`

---

### 18. 支払い方法管理
**概要**: 支払い手段（現金・クレジット等）の設定

**関連クラス**:
- **Controller**: `PaymentTypeController.java`
- **Service**: `PaymentTypeService.java`
- **Entity**: `PaymentType.java`
- **Repository**: `PaymentTypeRepository.java`

---

### 19. 税率設定
**概要**: 消費税率（軽減税率対応）の設定

**関連クラス**:
- **Controller**: `TaxRateController.java`
- **Service**: なし (Controller直接操作)
- **Entity**: `TaxRate.java`
- **Repository**: `TaxRateRepository.java`

---

### 20. 売り上げ分析
**概要**: 売上データの集計と分析

**関連クラス**:
- **Controller**: `SalesAnalysisController.java`
- **Service**: `PaymentService.java`
- **Entity**: 
  - `Payment.java`
  - `PaymentDetail.java`
- **Repository**: 
  - `PaymentRepository.java`
  - `PaymentDetailRepository.java`

---

### 21. 点検
**概要**: レジ締め等の点検作業

**関連クラス**:
- **Controller**: `InspectionLogController.java`
- **Service**: `InspectionLogService.java`
- **Entity**: `InspectionLog.java`
- **Repository**: `InspectionLogRepository.java`
- **DTO**: `InspectionLogRequest.java`

---

### 22. 点検履歴
**概要**: 過去の点検記録の参照

**関連クラス**:
- **Controller**: `InspectionLogController.java`
- **Service**: `InspectionLogService.java`
- **Entity**: `InspectionLog.java`
- **Repository**: `InspectionLogRepository.java`

---

## 店舗設定

### 23. プリンター管理
**概要**: レシートプリンタの登録と設定

**関連クラス**:
- **Controller**: `PrinterController.java`
- **Service**: `PrinterConfigService.java`
- **Entity**: 
  - `PrinterConfig.java`
  - `MenuPrinterMap.java` (メニューとプリンタの紐付け)
- **Repository**: 
  - `PrinterConfigRepository.java`
  - `MenuPrinterMapRepository.java`

---

### 24. プラン管理
**概要**: 飲み放題等のプランの設定（店舗設定として）

**関連クラス**:
- **Controller**: `AdminPlanController.java`
- **Service**: `PlanService.java`
- **Entity**: 
  - `Plan.java`
  - `PlanMenuGroupMap.java`
- **Repository**: 
  - `PlanRepository.java`
  - `PlanMenuGroupMapRepository.java`
- **DTO**: 
  - `PlanRequestDto.java`
  - `PlanResponseDto.java`

---

### 25. 端末管理
**概要**: オーダー端末の登録と管理

**関連クラス**:
- **Controller**: `AdminTerminalController.java`
- **Service**: `TerminalService.java`
- **Entity**: `Terminal.java`
- **Repository**: `TerminalRepository.java`
- **DTO**: `TerminalCreationDto.java`

---

### 26. ロゴ設定
**概要**: 店舗ロゴ画像のアップロードと管理

**関連クラス**:
- **Controller**: `AdminLogoController.java`
- **Service**: 
  - `LogoService.java`
  - `ImageUploadService.java`
- **Entity**: `Logo.java`
- **Repository**: `LogoRepository.java`

---

### 27. 店舗設定
**概要**: 店舗情報全般の設定

**関連クラス**:
- **Controller**: 
  - `AdminStoreController.java`
  - `AdminController.java` (管理画面全般)
- **Service**: `StoreService.java`
- **Entity**: `Store.java`
- **Repository**: `StoreRepository.java`

---

### 28. 時間帯管理
**概要**: メニュー提供時間帯の設定

**関連クラス**:
- **Controller**: `MenuTimeSlotController.java`
- **Service**: `MenuTimeSlotService.java`
- **Entity**: `MenuTimeSlot.java`
- **Repository**: `MenuTimeSlotRepository.java`
- **DTO**: `MenuTimeSlotRequest.java`

---

### 29. 座席グループ・座席管理
**概要**: 座席とグループの設定・編集

**関連クラス**:
- **Controller**: 
  - `SeatEditController.java`
  - `SeatController.java`
  - `SeatGroupRestController.java`
- **Service**: `SeatService.java`
- **Entity**: 
  - `Seat.java`
  - `SeatGroup.java`
- **Repository**: 
  - `SeatRepository.java`
  - `SeatGroupRepository.java`
- **DTO**: 
  - `SeatUpdateDto.java`
  - `SeatRequestDto.java`

---

### 30. スタッフ管理
**概要**: 従業員アカウントの管理

**関連クラス**:
- **Controller**: `UserController.java`
- **Service**: `UserService.java`
- **Entity**: `User.java`
- **Repository**: `UserRepository.java`

---

## 共通・補助クラス

### アプリケーション設定
- **メインクラス**: `PreSotukenApplication.java`
- **起動処理**: `StartupRunner.java`
- **設定**:
  - `config/WebConfig.java`
  - `config/WebSocketConfig.java`
- **例外処理**: `exception/GlobalExceptionHandler.java`

### ユーティリティ
- `util/CookieUtil.java`
- `service/CookieUtil.java`
- `service/ImageUploadService.java`

---

## ファイル構成サマリー

| カテゴリ | ファイル数 |
|---------|----------|
| Controller | 31 |
| Service | 23 |
| Entity | 26 |
| Repository | 25 |
| DTO | 23 |
| Config/Interceptor/Util | 7 |
| **合計** | **135** |

---

## 補足情報

### アーキテクチャパターン
PreSotukenは標準的な**3層アーキテクチャ**を採用しています：

1. **Controller層**: HTTPリクエストの受付とルーティング
2. **Service層**: ビジネスロジックの実装
3. **Repository層**: データベースアクセス（Spring Data JPA）

### 技術スタック
- **言語**: Java 21
- **フレームワーク**: Spring Boot
- **ORM**: JPA (Hibernate)
- **ビルドツール**: Gradle
- **リアルタイム通信**: WebSocket (STOMP)
- **プリンター制御**: ESC/POS コマンド

---

**最終更新**: 2026-01-30
