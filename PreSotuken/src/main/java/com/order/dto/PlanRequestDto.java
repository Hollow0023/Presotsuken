package com.order.dto; // 適切なパッケージに配置してね

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PlanRequestDto {
    private Integer planId; // 編集時はIDが必要
    private String planName;
    private String planDescription; // Planテーブルのplan_descriptionも入力できるようにする？
    private Integer storeId; // どの店舗のプランか

    // 紐づくメニューグループのIDリスト
    private List<Integer> menuGroupIds;
}