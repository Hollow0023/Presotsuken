package com.order.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.order.entity.Payment;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Integer> {

    Payment findByVisitVisitId(int visitId);
    
    // 個別会計機能用: visitIdから親会計（元の会計）のみを取得
    // parent_payment_id が NULL の Payment を返す
    Payment findByVisitVisitIdAndParentPaymentIsNull(int visitId);

    List<Payment> findByStoreStoreIdOrderByPaymentTimeDesc(Integer storeId);

    @Query("""
            SELECT p FROM Payment p
            WHERE p.store.storeId = :storeId
              AND (p.visitCancel = :isCancelled OR p.cancel = :isCancelled)
              AND (
                CASE 
                  WHEN :isCancelled = true THEN (p.visitCancel = true OR p.cancel = true)
                  ELSE (p.visitCancel = false AND p.cancel = false)
                END
              )
            ORDER BY p.paymentTime DESC
        """)
    List<Payment> findByStoreStoreIdAndCancelledStatus(
        @Param("storeId") Integer storeId,
        @Param("isCancelled") Boolean isCancelled);

    List<Payment> findByStoreStoreIdAndPaymentTimeBetween(Integer storeId, LocalDateTime start, LocalDateTime end);



    @Query("""
        SELECT COUNT(DISTINCT p.visit.visitId)
        FROM Payment p
        WHERE p.store.storeId = :storeId
          AND p.paymentTime >= :start
          AND p.paymentTime < :end
          AND p.visitCancel = false
          AND COALESCE(p.cancel, false) = false
    """)
    Long countCustomerVisits(
        @Param("storeId") Integer storeId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end);
    
    @Query("""
    	    SELECT SUM(p.total)
    	    FROM Payment p
            WHERE p.store.storeId = :storeId
              AND p.paymentTime BETWEEN :start AND :end
              AND p.visitCancel = false
              AND COALESCE(p.cancel, false) = false
              AND p.paymentType.isInspectionTarget = true
    	""")
    	BigDecimal sumCashSales(
    	    @Param("storeId") Integer storeId,
    	    @Param("start") LocalDateTime start,
    	    @Param("end") LocalDateTime end
    	);
    


}
