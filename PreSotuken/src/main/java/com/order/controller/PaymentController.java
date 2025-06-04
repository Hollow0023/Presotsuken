package com.order.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.order.dto.PaymentFinalizeRequest;
import com.order.entity.Payment;
import com.order.entity.PaymentDetail;
import com.order.entity.PaymentType;
import com.order.entity.Visit;
import com.order.repository.PaymentDetailRepository;
import com.order.repository.PaymentRepository;
import com.order.repository.PaymentTypeRepository;
import com.order.repository.VisitRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Controller
public class PaymentController {

    private final VisitRepository visitRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentDetailRepository paymentDetailRepository;
    private final PaymentTypeRepository paymentTypeRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @GetMapping("/payments")
    public String showPaymentDetail(@RequestParam("visitId") int visitId,
                                    @CookieValue(name = "storeId", required = false) Integer storeId,
                                    Model model) {
        // Visit の取得
        Visit visit = visitRepository.findById(visitId).orElse(null);
        if (visit == null) {
            return "redirect:/error";
        }

        // Payment の取得
        Payment payment = paymentRepository.findByVisitVisitId(visitId);
        if (payment == null) {
            return "redirect:/error";
        }

        // PaymentDetail の取得
        List<PaymentDetail> details = paymentDetailRepository.findByPaymentPaymentId(payment.getPaymentId());

        // 小計計算
        int subtotal = details.stream()
            .mapToInt(d -> d.getSubtotal().intValue())
            .sum();

        double discount = payment.getDiscount() != null ? payment.getDiscount() : 0;
        double total = subtotal - discount;

        List<PaymentType> paymentTypeList = paymentTypeRepository.findByStoreId(storeId);

        model.addAttribute("visit", visit);
        model.addAttribute("payment", payment);
        model.addAttribute("details", details);
        model.addAttribute("subtotal", subtotal);
        model.addAttribute("discount", discount);
        model.addAttribute("total", total);
        model.addAttribute("paymentTypeList", paymentTypeList);

        return "payment";
    }

    /**
     * 会計確定: Payment および Visit の退店時刻を更新
     */
    @Transactional
    @PostMapping("/payments/finalize")
    public ResponseEntity<Void> finalizePayment(@RequestBody PaymentFinalizeRequest req) {
        // ① Payment を取得
        Payment payment = paymentRepository.findById(req.getPaymentId())
            .orElseThrow(() -> new IllegalArgumentException("Invalid paymentId"));

        // ② PaymentType を ID から取得してセット
        if (req.getPaymentTypeId() != null) {
            PaymentType type = paymentTypeRepository.findById(req.getPaymentTypeId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid paymentTypeId"));
            payment.setPaymentType(type);
        }

        // ③ その他の値を設定
        payment.setSubtotal(req.getSubtotal());
        payment.setDiscount(req.getDiscount());
        payment.setDiscountReason(req.getDiscountReason());
        payment.setTotal(req.getTotal());
        payment.setPaymentTime(req.getPaymentTime());

        // ④ Visit 退店時刻を更新
        Visit visit = payment.getVisit();
        visit.setLeaveTime(req.getPaymentTime());
        messagingTemplate.convertAndSend("/topic/seats/" + visit.getSeat().getSeatId(), "LEAVE");
        visitRepository.save(visit);

        // ⑤ 保存
        paymentRepository.save(payment);
        return ResponseEntity.ok().build();
    }
}
