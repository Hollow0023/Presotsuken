package com.order.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.order.entity.PaymentDetail;

import jakarta.transaction.Transactional;

public interface PaymentDetailRepository extends JpaRepository<PaymentDetail, Integer> {

    List<PaymentDetail> findByPaymentPaymentId(int paymentId);

    @Transactional
    @Modifying
    @Query("DELETE FROM PaymentDetail pd WHERE pd.payment.paymentId = :paymentId")
    void deleteByPaymentPaymentId(@Param("paymentId") Integer paymentId);

    List<PaymentDetail> findByPaymentPaymentIdAndMenuIsPlanStarterTrue(Integer paymentId);

    // 現金売上（点検対象の支払い種別）
//    @Query("""
//        SELECT SUM(pd.subtotal)
//        FROM PaymentDetail pd
//        JOIN pd.payment p
//        JOIN p.paymentType pt
//        WHERE p.store.storeId = :storeId
//          AND p.paymentTime BETWEEN :start AND :end
//          AND pt.isInspectionTarget = true
//          AND p.visitCancel = false
//    """)
//    BigDecimal sumCashSales(
//        @Param("storeId") Integer storeId,
//        @Param("start") LocalDateTime start,
//        @Param("end") LocalDateTime end
//    );

    // 税率ごとの売上合計（点検対象の支払い種別）
    @Query("""
        SELECT pd.taxRate.taxRateId, SUM(pd.subtotal)
        FROM PaymentDetail pd
        JOIN pd.payment p
        JOIN p.paymentType pt
        WHERE p.store.storeId = :storeId
          AND p.paymentTime BETWEEN :start AND :end
          AND pt.isInspectionTarget = true
          AND p.visitCancel = false
        GROUP BY pd.taxRate.taxRateId
    """)
    List<Object[]> sumSalesByTaxRate(
        @Param("storeId") Integer storeId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );

    // 会計種別ごとの売上合計（すべて）
    @Query("""
        SELECT pt.typeName, SUM(pd.subtotal)
        FROM PaymentDetail pd
        JOIN pd.payment p
        JOIN p.paymentType pt
        WHERE p.store.storeId = :storeId
          AND p.paymentTime BETWEEN :start AND :end
          AND p.visitCancel = false
        GROUP BY pt.typeName
    """)
    List<Object[]> sumSalesByPaymentType(
        @Param("storeId") Integer storeId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );

    // 会計種別ごとの値引き合計（discountがnullでない）
    @Query("""
        SELECT pt.typeName, SUM(pd.discount)
        FROM PaymentDetail pd
        JOIN pd.payment p
        JOIN p.paymentType pt
        WHERE p.store.storeId = :storeId
          AND p.paymentTime BETWEEN :start AND :end
          AND pd.discount IS NOT NULL
          AND p.visitCancel = false
        GROUP BY pt.typeName
    """)
    List<Object[]> sumDiscountByPaymentType(
        @Param("storeId") Integer storeId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );
    
 // 総売上（キャンセル除外・全支払種別）
    @Query("""
        SELECT SUM(pd.subtotal)
        FROM PaymentDetail pd
        JOIN pd.payment p
        WHERE p.store.storeId = :storeId
          AND p.paymentTime BETWEEN :start AND :end
          AND p.visitCancel = false
    """)
    BigDecimal sumTotalSales(
        @Param("storeId") Integer storeId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );

    // 消費税額（税率指定）
    @Query("""
    	    SELECT SUM(pd.subtotal * pd.taxRate.rate)
    	    FROM PaymentDetail pd
    	    JOIN pd.payment p
    	    WHERE p.store.storeId = :storeId
    	      AND p.paymentTime BETWEEN :start AND :end
    	      AND p.visitCancel = false
    	      AND pd.taxRate.rate = :rate
    	""")
    	BigDecimal sumTaxAmount(
    	    @Param("storeId") Integer storeId,
    	    @Param("start") LocalDateTime start,
    	    @Param("end") LocalDateTime end,
    	    @Param("rate") BigDecimal rate
    	);


}
