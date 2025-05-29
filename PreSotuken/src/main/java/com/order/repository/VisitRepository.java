package com.order.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.order.entity.Visit;

@Repository
public interface VisitRepository extends JpaRepository<Visit, Integer> {
	List<Visit> findByStore_StoreId(Integer storeId);
	Visit findFirstBySeat_SeatIdAndLeaveTimeIsNullOrderByVisitTimeDesc(int seatId);
	List<Visit> findByStore_StoreIdAndLeaveTimeIsNull(Integer storeId);
	Visit findTopByStore_StoreIdAndSeat_SeatIdOrderByVisitTimeDesc(Integer storeId, Integer seatId);




}
