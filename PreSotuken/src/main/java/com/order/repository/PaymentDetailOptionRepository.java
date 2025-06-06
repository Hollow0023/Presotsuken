package com.order.repository;

import java.util.List; // 追加

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.order.entity.PaymentDetail; // 追加
import com.order.entity.PaymentDetailOption;

@Repository
public interface PaymentDetailOptionRepository extends JpaRepository<PaymentDetailOption, Integer> {
    // PaymentDetail に紐づく PaymentDetailOption のリストを取得するメソッド
    List<PaymentDetailOption> findByPaymentDetail(PaymentDetail paymentDetail);
}