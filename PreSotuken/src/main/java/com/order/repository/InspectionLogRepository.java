package com.order.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.order.entity.InspectionLog;

public interface InspectionLogRepository extends JpaRepository<InspectionLog, Integer> {
    List<InspectionLog> findByStore_StoreIdAndInspectionTimeBetween(Integer storeId, LocalDateTime start, LocalDateTime end);
}
