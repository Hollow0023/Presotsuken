package com.order.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query; // Queryアノテーションのために追加
import org.springframework.stereotype.Repository;

import com.order.entity.OptionGroup;

@Repository
public interface OptionGroupRepository extends JpaRepository<OptionGroup, Integer> {
    List<OptionGroup> findByStoreId(Integer storeId);

    // OptionGroup から MenuOption を JOIN し、MenuOption の持つ Menu エンティティの menuId でフィルタリング
    @Query("SELECT og FROM OptionGroup og JOIN MenuOption mo ON og.optionGroupId = mo.optionGroupId WHERE mo.menu.menuId = :menuId")
    List<OptionGroup> findByMenuId(@org.springframework.data.repository.query.Param("menuId") Integer menuId);
}