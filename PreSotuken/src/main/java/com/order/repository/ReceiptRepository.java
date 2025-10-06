package com.order.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.order.entity.Receipt;

@Repository
public interface ReceiptRepository extends JpaRepository<Receipt, Integer> {
    List<Receipt> findByPaymentPaymentIdOrderByIssuedAtDesc(Integer paymentId);
    Optional<Receipt> findByReceiptNo(String receiptNo);
    Optional<Receipt> findByIdempotencyKey(String idempotencyKey);
}
