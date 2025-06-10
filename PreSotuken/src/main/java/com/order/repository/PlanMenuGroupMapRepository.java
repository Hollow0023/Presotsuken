package com.order.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.order.entity.PlanMenuGroupMap;
import com.order.entity.PlanMenuGroupMapId; // ここでPlanMenuGroupMapIdを正しくインポートできていればOK

@Repository
public interface PlanMenuGroupMapRepository extends JpaRepository<PlanMenuGroupMap, PlanMenuGroupMapId> {
    List<PlanMenuGroupMap> findByPlanId(Integer planId);
}