package com.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.order.entity.Store;

@Repository
public interface StoreRepository extends JpaRepository<Store, Integer> {
	
}
