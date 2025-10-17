package com.order.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.order.entity.Payment;
import com.order.entity.Visit;
import com.order.repository.PaymentRepository;
import com.order.repository.VisitRepository;

@Service
public class PaymentLookupService {

    @Autowired
    private VisitRepository visitRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    public Payment findPaymentBySeatId(int seatId) {
        Visit visit = visitRepository.findFirstBySeat_SeatIdAndLeaveTimeIsNullOrderByVisitTimeDesc(seatId);
        if (visit == null) {
            return null;
        }
        // 個別会計機能対応: 親会計（元の会計）のみを取得
        return paymentRepository.findByVisitVisitIdAndParentPaymentIsNull(visit.getVisitId());
    }
} 
