package com.order.dto; // 適切なパッケージに配置してね

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PlanResponseDto {
    private Integer planId;
    private String planName;
    private String planDescription;
    private Integer storeId; // どの店舗のプランか

    // 紐づくメニューグループの名前リスト（表示用）
    private List<String> menuGroupNames;
    // 紐づくメニューグループのIDリスト（編集時の初期表示用）
    private List<Integer> menuGroupIds;
}