package com.order.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.order.entity.MenuOption;

@Repository
public interface MenuOptionRepository extends JpaRepository<MenuOption, Integer> {
	void deleteByMenu_MenuId(Integer menuId); 

    // ★★★ これが正しい！ findByMenuId(int) ではなく findByMenu_MenuId(Integer) を使う
    List<MenuOption> findByMenu_MenuId(Integer menuId);
    
    // オプショングループIDでMenuOptionを検索
    List<MenuOption> findByOptionGroupId(Integer optionGroupId);
    
    // オプショングループに関連するメニュー情報を取得
    @Query("SELECT mo FROM MenuOption mo JOIN FETCH mo.menu WHERE mo.optionGroupId = :optionGroupId")
    List<MenuOption> findByOptionGroupIdWithMenu(@Param("optionGroupId") Integer optionGroupId);
}
