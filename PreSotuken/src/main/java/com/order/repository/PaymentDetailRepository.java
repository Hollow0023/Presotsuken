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

    @Query("""
        SELECT pd.menu.menuName,
               SUM(pd.quantity),
               SUM(pd.subtotal),
               SUM(pd.subtotal * (1 + pd.taxRate.rate))

        FROM PaymentDetail pd
        JOIN pd.payment p
        WHERE p.store.storeId = :storeId
          AND p.paymentTime >= :start AND p.paymentTime < :end
          AND p.visitCancel = false
          AND p.cancel = false
        GROUP BY pd.menu.menuName
        ORDER BY pd.menu.menuName
    """)
    List<Object[]> sumMenuQuantityByTime(
        @Param("storeId") Integer storeId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );

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
          AND p.cancel = false
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
          AND p.cancel = false
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
          AND p.cancel = false
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
          AND p.cancel = false
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
              AND p.cancel = false
              AND pd.taxRate.rate = :rate
    	""")
    	BigDecimal sumTaxAmount(
    	    @Param("storeId") Integer storeId,
    	    @Param("start") LocalDateTime start,
    	    @Param("end") LocalDateTime end,
    	    @Param("rate") BigDecimal rate
    	);
    
    /**
     * 指定された期間、店舗の支払いタイプおよび税率ごとの合計売上を取得します。
     * 割り勘子会計を含む全ての会計を正しく集計します。
     * @param storeId 店舗ID
     * @param start 集計開始日時 (以上)
     * @param end 集計終了日時 (未満)
     * @return 支払いタイプ名、税率ID、合計金額のリスト (例: ["現金", 1, 1000.00], ["現金", 2, 50.00])
     */
    @Query(value = """
        SELECT childPt.type_name, tr.tax_rate_id, 
               SUM(pd.subtotal * (childP.total / NULLIF(parentP.total, 0)))
        FROM payment childP
        JOIN payment parentP ON childP.parent_payment_id = parentP.payment_id
        JOIN payment_type childPt ON childP.payment_type_id = childPt.type_id
        JOIN payment_detail pd ON pd.payment_id = parentP.payment_id
        JOIN tax_rate tr ON pd.tax_rate_id = tr.tax_rate_id
        WHERE childP.store_id = :storeId
          AND childP.payment_time >= :start
          AND childP.payment_time < :end
          AND childP.visit_cancel = false
          AND childP.cancel = false
          AND parentP.total != 0
        GROUP BY childPt.type_name, tr.tax_rate_id
        
        UNION ALL
        
        SELECT pt.type_name, tr.tax_rate_id, SUM(pd.subtotal)
        FROM payment_detail pd
        JOIN payment p ON pd.payment_id = p.payment_id
        JOIN payment_type pt ON p.payment_type_id = pt.type_id
        JOIN tax_rate tr ON pd.tax_rate_id = tr.tax_rate_id
        WHERE p.store_id = :storeId
          AND p.payment_time >= :start
          AND p.payment_time < :end
          AND p.visit_cancel = false
          AND p.cancel = false
          AND p.parent_payment_id IS NULL
        GROUP BY pt.type_name, tr.tax_rate_id
        
        ORDER BY 1, 2
    """, nativeQuery = true)
    List<Object[]> sumSalesByPaymentTypeAndTaxRate(
        @Param("storeId") Integer storeId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );


}
