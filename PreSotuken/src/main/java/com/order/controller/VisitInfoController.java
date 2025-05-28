package com.order.controller;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.order.entity.Payment;
import com.order.entity.Visit;
import com.order.repository.PaymentDetailRepository;
import com.order.repository.PaymentRepository;
import com.order.repository.VisitRepository;

import jakarta.transaction.Transactional;

@RestController
@RequestMapping("/api")
public class VisitInfoController {

    private final VisitRepository visitRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentDetailRepository paymentDetailRepository;

    //コンストラクタ
    public VisitInfoController(
        VisitRepository visitRepository,
        PaymentRepository paymentRepository,
        PaymentDetailRepository paymentDetailRepository
    ) {
        this.visitRepository = visitRepository;
        this.paymentRepository = paymentRepository;
        this.paymentDetailRepository = paymentDetailRepository;
    }

    @GetMapping("/visit-info")
    public Map<String, Object> getVisitInfo(@RequestParam("seatId") int seatId) {
        Map<String, Object> result = new HashMap<>();
        Visit visit = visitRepository.findFirstBySeat_SeatIdAndLeaveTimeIsNullOrderByVisitTimeDesc(seatId);

        if (visit != null) {
            result.put("visiting", true);
            result.put("visitId", visit.getVisitId());  // ←★コレ追加
            result.put("numberOfPeople", visit.getNumberOfPeople());

            LocalDateTime now = LocalDateTime.now();
            long minutes = Duration.between(visit.getVisitTime(), now).toMinutes();
            result.put("elapsedMinutes", minutes);
        } else {
            result.put("visiting", false);
        }

        return result;
    }
    
    @DeleteMapping("/delete-visit")
    @Transactional
    public ResponseEntity<Void> deleteVisitAndPayment(@RequestParam("seatId") int seatId) {
        Visit visit = visitRepository.findFirstBySeat_SeatIdAndLeaveTimeIsNullOrderByVisitTimeDesc(seatId);
        if (visit != null) {
            Payment payment = paymentRepository.findByVisitVisitId(visit.getVisitId());
            if (payment != null) {
                // PaymentDetailの削除
                paymentDetailRepository.deleteByPaymentPaymentId(payment.getPaymentId());

                // Paymentの削除
                paymentRepository.delete(payment);
            }

            // Visitの削除
            visitRepository.delete(visit);

            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

}
