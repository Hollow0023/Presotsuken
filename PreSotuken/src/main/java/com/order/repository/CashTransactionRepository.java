package com.order.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.order.entity.CashTransaction;

public interface CashTransactionRepository extends JpaRepository<CashTransaction, Integer> {
    List<CashTransaction> findByStore_StoreIdAndTransactionTimeBetween(Integer storeId, LocalDateTime start, LocalDateTime end);
    List<CashTransaction> findByStore_StoreIdAndTypeAndTransactionTimeBetween(Integer storeId, String type, LocalDateTime start, LocalDateTime end);

    
    @Query("""
            SELECT COALESCE(SUM(ct.amount), 0)
            FROM CashTransaction ct
            WHERE ct.store.storeId = :storeId
              AND ct.type = :type
              AND ct.transactionTime BETWEEN :start AND :end
        """)
        BigDecimal sumAmountByType(
            @Param("storeId") Integer storeId,
            @Param("type") String type, // "IN" or "OUT"
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
        );
}
