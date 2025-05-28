package com.order.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.order.entity.SeatGroup;
@Repository
public interface SeatGroupRepository extends JpaRepository<SeatGroup, Integer> {
	List<SeatGroup> findByStore_StoreId(Integer storeId);  //座席グループを店舗IDで検索

}
