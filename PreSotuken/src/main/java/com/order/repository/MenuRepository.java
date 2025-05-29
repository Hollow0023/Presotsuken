package com.order.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.order.entity.Menu;

@Repository
public interface MenuRepository extends JpaRepository<Menu, Integer> {
	List<Menu> findByStore_StoreId(Integer storeId);

}
