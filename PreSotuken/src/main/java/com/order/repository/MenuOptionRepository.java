package com.order.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.order.entity.MenuOption;

@Repository
public interface MenuOptionRepository extends JpaRepository<MenuOption, Integer> {
	void deleteByMenu_MenuId(Integer menuId); 

    // ★★★ これが正しい！ findByMenuId(int) ではなく findByMenu_MenuId(Integer) を使う
    List<MenuOption> findByMenu_MenuId(Integer menuId); 
}
