package com.order.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.order.entity.PaymentDetail;

import jakarta.transaction.Transactional;

public interface PaymentDetailRepository extends JpaRepository<PaymentDetail, Integer> {
    List<PaymentDetail> findByPaymentPaymentId(int paymentId);
    
    @Transactional
    @Modifying
    @Query("DELETE FROM PaymentDetail pd WHERE pd.payment.paymentId = :paymentId")
    void deleteByPaymentPaymentId(Integer paymentId);
    
    List<PaymentDetail> findByPaymentPaymentIdAndMenuIsPlanStarterTrue(Integer paymentId);

}
