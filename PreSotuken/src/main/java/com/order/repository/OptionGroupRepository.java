package com.order.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.order.entity.OptionGroup;

@Repository
public interface OptionGroupRepository extends JpaRepository<OptionGroup, Integer> {
    List<OptionGroup> findByStoreId(int storeId);
    
    @Query("SELECT og FROM OptionGroup og JOIN MenuOption mo ON og.optionGroupId = mo.optionGroupId WHERE mo.menuId = :menuId")
    List<OptionGroup> findByMenuId(@Param("menuId") Integer menuId);

}
