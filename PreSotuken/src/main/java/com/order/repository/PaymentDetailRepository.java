package com.order.repository;

import java.util.List;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.jpa.repository.JpaRepository;

import com.order.entity.PaymentDetail;

import jakarta.transaction.Transactional;

public interface PaymentDetailRepository extends JpaRepository<PaymentDetail, Integer> {
    List<PaymentDetail> findByPaymentPaymentId(int paymentId);
    
    @Transactional
    @Modifying
    @Query("DELETE FROM PaymentDetail pd WHERE pd.payment.paymentId = :paymentId")
    void deleteByPaymentPaymentId(Integer paymentId);
}
