package com.order.controller;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.order.entity.Payment;
import com.order.entity.PaymentDetail;
import com.order.entity.Visit;
import com.order.repository.PaymentDetailRepository;
import com.order.repository.PaymentRepository;
import com.order.repository.VisitRepository;
import com.order.service.PaymentLookupService;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class VisitInfoController {

    private final VisitRepository visitRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentDetailRepository paymentDetailRepository;
    private final SimpMessagingTemplate messagingTemplate;   
    
    @Autowired
    private PaymentLookupService paymentLookupService;

    @GetMapping("/visit-info")
    public Map<String, Object> getVisitInfo(@RequestParam("seatId") int seatId, @RequestParam("storeId") int storeId) {
        Map<String, Object> result = new HashMap<>();
//        Visit visit = visitRepository.findFirstBySeat_SeatIdAndLeaveTimeIsNullOrderByVisitTimeDesc(seatId);
        Visit visit = visitRepository
        	    .findFirstBySeat_Store_StoreIdAndSeat_SeatIdAndLeaveTimeIsNullOrderByVisitTimeDesc(storeId, seatId);


        if (visit != null) {
            result.put("visiting", true);
            result.put("visitId", visit.getVisitId());  
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
            messagingTemplate.convertAndSend("/topic/seats/" + seatId, "LEAVE");


            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    
    
    
    @GetMapping("/total-amount")
    public Map<String, Object> getTotalAmount(@RequestParam int seatId) {
        Payment payment = paymentLookupService.findPaymentBySeatId(seatId);
        if (payment == null) {
            return Map.of("total", 0);
        }

        List<PaymentDetail> details = paymentDetailRepository.findByPaymentPaymentId(payment.getPaymentId());

        int total = 0;
        for (PaymentDetail d : details) {
            double rate = d.getTaxRate().getRate();
            int subtotal = (int) Math.round(d.getMenu().getPrice() * d.getQuantity() * (1 + rate));
            total += subtotal;
        }

        return Map.of("total", total);
    }

}
