package com.order.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.order.entity.MenuGroup;

public interface MenuGroupRepository extends JpaRepository<MenuGroup, Integer> {
	List<MenuGroup> findByStore_StoreId(Integer storeId);
}
