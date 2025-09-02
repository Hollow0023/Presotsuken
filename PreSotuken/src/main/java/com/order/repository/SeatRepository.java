package com.order.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.order.entity.Seat;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Integer> {
	List<Seat> findByStore_StoreIdAndSeatGroup_SeatGroupId(Integer storeId, Integer seatGroupId);
	List<Seat> findByStore_StoreId(Integer storeId);
        List<Seat> findByStore_StoreIdOrderBySeatNameAsc(Integer storeId);
	Seat findBySeatId(Integer seatId);
}
