package com.order.dto;

import java.util.List;

import com.order.entity.Menu;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MenuWithOptionsDTO {
    // フィールドはプリミティブ型やIDで持つのが一般的
    private Integer menuId;
    private String menuName;
    private Double price;
    
    private Integer taxRateId;
    private Double taxRateValue; // 税率の値そのもの (例: 0.08, 0.1)

    private Integer menuGroupId;
    private String menuGroupName;
    private Boolean menuGroupIsPlanTarget; // is_plan_target
    private Integer menuGroupSortOrder; // sort_order

    private String menuImage;
    private String description; // MenuエンティティのmenuDescriptionに対応
    private Double priceWithTax; // int ではなく Double に修正（税込み価格は小数点以下を含むため）
    private Boolean isSoldOut;

    private Boolean isPlanStarter;
    private Integer planId;

    private List<OptionGroupDTO> optionGroups;

    public MenuWithOptionsDTO(Menu menu) {
        this.menuId = menu.getMenuId();
        this.menuName = menu.getMenuName();
        this.price = menu.getPrice();

        if (menu.getTaxRate() != null) {
            this.taxRateId = menu.getTaxRate().getTaxRateId();
            this.taxRateValue = menu.getTaxRate().getRate();
            this.priceWithTax = menu.getPrice() * (1 + menu.getTaxRate().getRate());
        } else {
            this.taxRateId = null;
            this.taxRateValue = 0.0;
            this.priceWithTax = menu.getPrice();
        }

        if (menu.getMenuGroup() != null) {
            this.menuGroupId = menu.getMenuGroup().getGroupId();
            this.menuGroupName = menu.getMenuGroup().getGroupName();
            this.menuGroupIsPlanTarget = menu.getMenuGroup().getIsPlanTarget();
            this.menuGroupSortOrder = menu.getMenuGroup().getSortOrder();
        } else {
            this.menuGroupId = null;
            this.menuGroupName = null;
            this.menuGroupIsPlanTarget = false;
            this.menuGroupSortOrder = Integer.MAX_VALUE; // ソート順の末尾に
        }

        this.menuImage = menu.getMenuImage();
        this.description = menu.getMenuDescription();
        this.isSoldOut = menu.getIsSoldOut();

        this.isPlanStarter = menu.getIsPlanStarter();
        this.planId = menu.getPlanId();

        // オプション情報のマッピング (MenuエンティティにList<MenuOption>がある場合など)
        // ここはMenuService.toDtoと同じロジックになるはず
        // 必要に応じて Menuエンティティからオプションデータを取得し、OptionGroupDTOとOptionItemDTOに変換してセットする
        // 例: (menu.getMenuOptions() != null) のような関連プロパティがあれば
        // this.optionGroups = menu.getMenuOptions().stream()...
    }
}