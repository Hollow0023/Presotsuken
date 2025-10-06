package com.order.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.order.entity.Receipt;

public interface ReceiptRepository extends JpaRepository<Receipt, Integer> {
    
    // 会計IDから領収書一覧を取得（取消を含む）
    List<Receipt> findByPaymentPaymentIdOrderByIssuedAtDesc(Integer paymentId);
    
    // 会計IDから未取消の領収書一覧を取得
    List<Receipt> findByPaymentPaymentIdAndVoidedFalseOrderByIssuedAtDesc(Integer paymentId);
    
    // idempotencyKeyから領収書を検索（二重発行防止用）
    Optional<Receipt> findByIdempotencyKey(String idempotencyKey);
    
    // 店舗IDと日付から領収書一覧を取得
    List<Receipt> findByStoreStoreIdOrderByIssuedAtDesc(Integer storeId);
}
