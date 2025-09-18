package com.order.dto;

import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor; // デフォルトコンストラクタを生成
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor // 新規作成フォームで空のDTOをバインドするため
public class MenuForm {
    private Integer menuId;
    private String menuName;
    private String menuImage;
    private Double price;
    private String menuDescription;
    private String receiptLabel;
    private Boolean isSoldOut;

    // 関連エンティティの識別子と表示用情報
    // MenuTimeSlotのIDと表示名、時間
    private Integer timeSlotTimeSlotId; // DBカラム名に合わせて`timeSlot.timeSlotId`をDTOのフィールドにマッピング
    private String timeSlotName; // 表示用 (APIから取得したMenuTimeSlotオブジェクトから設定される)
    private String timeSlotStartTime; // LocalTimeをStringで返す (APIから取得したMenuTimeSlotオブジェクトから設定される)
    private String timeSlotEndTime; // LocalTimeをStringで返す (APIから取得したMenuTimeSlotオブジェクトから設定される)

    // TaxRateのIDと税率
    private Integer taxRateTaxRateId; // DBカラム名に合わせて`taxRate.taxRateId`をDTOのフィールドにマッピング
    private Double taxRateRate; // 表示用 (APIから取得したTaxRateオブジェクトから設定される)

    // MenuGroupのIDと名前
    private Integer menuGroupGroupId; // DBカラム名に合わせて`menuGroup.groupId`をDTOのフィールドにマッピング
    private String menuGroupName; // 表示用 (APIから取得したMenuGroupオブジェクトから設定される)

    // オプション選択用（IDリスト）
    private List<Integer> optionGroupIds;
    // プリンター選択用（IDリスト）
    private Integer printerId;

    // 飲み放題関連のフィールド
    private Boolean isPlanStarter;
    private Integer planId; 
    // private String planName; // もしプラン名も表示したいなら追加（APIからのMenuFormにないため、別途取得が必要）

    // MenuAddService.getMenuFormById で Menu エンティティからこのDTOへ値を写し替えているため、
    // エンティティを受け取る追加コンストラクタは定義していない。
    // 必要なフィールドはサービス層で個別に setter を呼び出して設定する。
}