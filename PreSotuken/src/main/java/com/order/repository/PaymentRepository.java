package com.order.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.order.entity.Payment;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Integer> {

    Payment findByVisitVisitId(int visitId);



    @Query("""
        SELECT COUNT(DISTINCT p.visit.visitId)
        FROM Payment p
        WHERE p.store.storeId = :storeId
          AND p.paymentTime >= :start
          AND p.paymentTime < :end
          AND p.visitCancel = false
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
    	      AND p.paymentType.isInspectionTarget = true
    	""")
    	BigDecimal sumCashSales(
    	    @Param("storeId") Integer storeId,
    	    @Param("start") LocalDateTime start,
    	    @Param("end") LocalDateTime end
    	);
    


}
