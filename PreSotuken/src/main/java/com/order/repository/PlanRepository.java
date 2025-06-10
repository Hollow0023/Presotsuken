package com.order.repository; // パッケージ名は適宜変更してね

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.order.entity.Plan;

@Repository
public interface PlanRepository extends JpaRepository<Plan, Integer> {
    // PlanIdで検索するだけならJpaRepositoryのfindByIdが使えるね
	List<Plan>findByStore_StoreId(Integer storeId);
}