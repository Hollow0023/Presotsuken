package com.order.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.order.entity.InspectionLog;

public interface InspectionLogRepository extends JpaRepository<InspectionLog, Integer> {
    List<InspectionLog> findByStore_StoreIdAndInspectionTimeBetween(Integer storeId, LocalDateTime start, LocalDateTime end);
    
    /**
     * 指定された店舗IDと期間内に点検ログが存在するかどうかをチェックします。
     * @param storeId 店舗ID
     * @param start 集計開始日時 (以上)
     * @param end 集計終了日時 (未満)
     * @return 存在すればtrue、存在しなければfalse
     */
    @Query("""
        SELECT COUNT(il) > 0
        FROM InspectionLog il
        WHERE il.store.storeId = :storeId
          AND il.inspectionTime >= :start
          AND il.inspectionTime < :end
    """)
    boolean existsByStoreIdAndInspectionTimeBetween(
        @Param("storeId") Integer storeId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );
}
