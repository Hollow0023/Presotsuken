package com.order.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.order.entity.PlanMenuGroupMap;
import com.order.entity.PlanMenuGroupMapId; // ここでPlanMenuGroupMapIdを正しくインポートできていればOK

import jakarta.transaction.Transactional;

@Repository
public interface PlanMenuGroupMapRepository extends JpaRepository<PlanMenuGroupMap, PlanMenuGroupMapId> {
    List<PlanMenuGroupMap> findByPlanId(Integer planId);

    // ★追加：planIdに基づいて紐付けを全て削除するカスタムクエリ
    @Modifying // DML操作（UPDATE, DELETE）であることを示す
    @Transactional // この操作はトランザクション内で実行される必要がある
    @Query("DELETE FROM PlanMenuGroupMap pmm WHERE pmm.planId = :planId")
    void deleteByPlanId(@Param("planId") Integer planId);
    
    @Query("SELECT COUNT(pmm) > 0 FROM PlanMenuGroupMap pmm WHERE pmm.menuGroupId = :menuGroupId AND pmm.planId != :planId")
    boolean existsByMenuGroupIdAndPlanIdNot(@Param("menuGroupId") Integer menuGroupId, @Param("planId") Integer planId);
    
}