package com.order.repository;

import java.time.LocalTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.order.entity.Menu;

@Repository
public interface MenuRepository extends JpaRepository<Menu, Integer> {
	List<Menu> findByStore_StoreId(Integer storeId);
	
	@Query("SELECT m FROM Menu m WHERE :now BETWEEN m.timeSlot.startTime AND m.timeSlot.endTime")
	List<Menu> findMenusAvailableAt(@Param("now") LocalTime now);



}
