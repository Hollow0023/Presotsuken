package com.order.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * オプション削除時のメニュー関連チェック結果を格納するDTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OptionDeletionCheckDTO {
    
    // オプションがメニューに紐づいているかどうか
    private boolean hasLinkedMenus;
    
    // 紐づいているメニューの情報リスト
    private List<LinkedMenuInfo> linkedMenus;
    
    /**
     * 紐づいているメニューの基本情報
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LinkedMenuInfo {
        private Integer menuId;
        private String menuName;
    }
}