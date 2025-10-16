# userIdクッキー維持の修正

## 問題の概要

seat-listページから入店処理を行った際、担当者選択で設定したuserIdのクッキーがundefinedになる問題がありました。

## 原因

1. ユーザーがseat-listページで担当者を選択すると、JavaScriptの`setUserIdCookie()`関数によりuserIdがクッキーに保存される
2. 座席を選択して入店フォームを送信すると、`/visits`エンドポイント（VisitController.createVisit）にPOSTリクエストが送信される
3. VisitControllerは入店情報を登録後、`/seats?storeId=...`にリダイレクトする
4. **この際、userIdクッキーがレスポンスに含まれないため、リダイレクト後にクッキーが失われる**

## 修正内容

`VisitController.java`の`createVisit()`メソッドに以下の変更を加えました：

### 1. パラメータの追加
```java
HttpServletRequest request,
HttpServletResponse response,
```

### 2. userIdクッキーの維持処理を追加
```java
// userIdクッキーを維持（リクエストから取得してレスポンスに設定し直す）
if (request.getCookies() != null) {
    for (Cookie cookie : request.getCookies()) {
        if ("userId".equals(cookie.getName())) {
            String userIdValue = cookie.getValue();
            // 有効なuserIdの場合のみクッキーを再設定
            if (userIdValue != null && !userIdValue.isEmpty() && 
                !"null".equals(userIdValue) && !"undefined".equals(userIdValue)) {
                Cookie userIdCookie = new Cookie("userId", userIdValue);
                userIdCookie.setPath("/");
                userIdCookie.setMaxAge(60 * 60 * 24 * 30); // 30日間有効
                response.addCookie(userIdCookie);
            }
            break;
        }
    }
}
```

## 動作フロー（修正後）

1. ユーザーが担当者を選択 → JavaScriptでuserIdクッキーを設定
2. 座席を選択して入店フォームを送信 → `/visits`にPOST
3. VisitControllerがリクエストからuserIdクッキーを読み取る
4. 入店情報を登録
5. **userIdクッキーをレスポンスに再設定**
6. `/seats`にリダイレクト
7. リダイレクト後もuserIdクッキーが維持される

## 技術的な詳細

### クッキーのバリデーション
以下の無効な値はクッキーに保存されません：
- `null` (文字列としての"null")
- `undefined` (文字列としての"undefined")
- 空文字列
- null値

### クッキーの設定
- **パス**: `/` (全サイトで有効)
- **有効期限**: 30日間 (60 * 60 * 24 * 30秒)

## 影響範囲

- 修正対象: `VisitController.java`の`createVisit()`メソッドのみ
- 他の機能への影響: なし（既存の動作を維持）
- 互換性: 既存のコードとの互換性を保持

## テスト方法

1. seat-listページにアクセス
2. 担当者をドロップダウンから選択
3. 空席の座席をクリックして入店モーダルを開く
4. 人数を入力して「入店」ボタンをクリック
5. リダイレクト後、担当者の選択が維持されていることを確認
6. ブラウザの開発者ツールでクッキーを確認し、userIdが正しく設定されていることを確認
