package com.order.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.order.dto.PaymentFinalizeRequest;
import com.order.entity.Payment;
import com.order.entity.PaymentDetail;
import com.order.entity.PaymentType;
import com.order.entity.User;
import com.order.entity.Visit;
import com.order.entity.Seat;
import com.order.dto.PaymentHistoryUpdateRequest;
import com.order.repository.PaymentDetailRepository;
import com.order.repository.PaymentRepository;
import com.order.repository.PaymentTypeRepository;
import com.order.repository.UserRepository;
import com.order.repository.VisitRepository;
import com.order.repository.SeatRepository;

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
    private final UserRepository userRepository;
    private final SeatRepository seatRepository;

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
        
        model.addAttribute("userList", userRepository.findByStore_StoreId(storeId));
        model.addAttribute("visit", visit);
        model.addAttribute("payment", payment);
        model.addAttribute("details", details);
        model.addAttribute("subtotal", subtotal);
        model.addAttribute("discount", discount);
        model.addAttribute("total", total);
        model.addAttribute("paymentTypeList", paymentTypeList);

        return "payment";
    }

    @GetMapping("/payments/history")
    public String showPaymentHistory(@CookieValue(name = "storeId", required = false) Integer storeId,
                                     @RequestParam(name = "filter", required = false, defaultValue = "active") String filter,
                                     Model model) {
        List<Payment> payments;
        
        // フィルタパラメータに基づいて会計履歴を取得
        if ("cancelled".equals(filter)) {
            // キャンセルされた履歴のみ表示
            payments = paymentRepository.findByStoreStoreIdAndCancelOrderByPaymentTimeDesc(storeId, true);
        } else {
            // デフォルト: 有効な会計履歴のみ表示（キャンセルされていないもの）
            payments = paymentRepository.findByStoreStoreIdAndCancelOrderByPaymentTimeDesc(storeId, false);
        }
        
        Map<Integer, Double> subtotalMap = new HashMap<>();
        for (Payment p : payments) {
            double subtotal = paymentDetailRepository.findByPaymentPaymentId(p.getPaymentId())
                    .stream()
                    .mapToDouble(pd -> {
                        double base = pd.getSubtotal() != null ? pd.getSubtotal() : 0;
                        double detailDiscount = pd.getDiscount() != null ? pd.getDiscount() : 0;
                        double net = base - detailDiscount;
                        return net > 0 ? net : 0;
                    })
                    .sum();
            subtotalMap.put(p.getPaymentId(), subtotal);
        }
        model.addAttribute("payments", payments);
        model.addAttribute("subtotalMap", subtotalMap);
        model.addAttribute("currentFilter", filter);
        return "paymentHistory";
    }

    @GetMapping("/payments/history/detail")
    public String showPaymentDetail(
            @RequestParam("paymentId") Integer paymentId,
            @CookieValue(name = "storeId", required = false) Integer storeId,
            @CookieValue(name = "userId", required = false) Integer userId,
            Model model) {
        model.addAttribute("paymentId", paymentId);
        model.addAttribute("userId", userId);
        return "payment-detail";
    }

    @GetMapping("/payments/history/{paymentId}")
    public ResponseEntity<Map<String, Object>> getPaymentHistoryDetail(
            @CookieValue(name = "storeId", required = false) Integer storeId,
            @PathVariable("paymentId") Integer paymentId) {
        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null || !payment.getStore().getStoreId().equals(storeId)) {
            return ResponseEntity.notFound().build();
        }

        List<PaymentDetail> details = paymentDetailRepository.findByPaymentPaymentId(paymentId);
        List<Map<String, Object>> detailList = details.stream().map(d -> {
            Map<String, Object> m = new HashMap<>();
            m.put("paymentDetailId", d.getPaymentDetailId());
            m.put("menuName", d.getMenu().getMenuName());
            m.put("quantity", d.getQuantity());
            double baseSubtotal = d.getSubtotal() != null ? d.getSubtotal() : 0;
            double detailDiscount = d.getDiscount() != null ? d.getDiscount() : 0;
            double netSubtotalWithoutTax = Math.max(baseSubtotal - detailDiscount, 0);
            double taxRate = d.getTaxRate() != null ? d.getTaxRate().getRate() : 0;
            double subtotalWithTax = netSubtotalWithoutTax * (1 + taxRate);
            m.put("subtotalWithoutTax", netSubtotalWithoutTax);
            m.put("subtotalWithTax", subtotalWithTax);
            m.put("price", d.getMenu().getPrice());
            m.put("taxRate", taxRate);
            m.put("discount", detailDiscount);
            return m;
        }).collect(Collectors.toList());

        List<Map<String, Object>> seatList = seatRepository.findByStore_StoreIdOrderBySeatNameAsc(storeId).stream().map(s -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", s.getSeatId());
            m.put("name", s.getSeatName());
            return m;
        }).collect(Collectors.toList());

        List<Map<String, Object>> userList = userRepository.findByStore_StoreId(storeId).stream().map(u -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", u.getUserId());
            m.put("name", u.getUserName());
            return m;
        }).collect(Collectors.toList());

        List<Map<String, Object>> paymentTypeList = paymentTypeRepository.findByStoreId(storeId).stream().map(pt -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", pt.getTypeId());
            m.put("name", pt.getTypeName());
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        double subtotal = details.stream()
                .mapToDouble(d -> {
                    double base = d.getSubtotal() != null ? d.getSubtotal() : 0;
                    double detailDiscount = d.getDiscount() != null ? d.getDiscount() : 0;
                    double net = base - detailDiscount;
                    return net > 0 ? net : 0;
                })
                .sum();
        result.put("seatId", payment.getVisit().getSeat().getSeatId());
        result.put("seatName", payment.getVisit().getSeat().getSeatName());
        result.put("paymentTypeId", payment.getPaymentType() != null ? payment.getPaymentType().getTypeId() : null);
        result.put("cashierId", payment.getCashier() != null ? payment.getCashier().getUserId() : null);
        result.put("cashierName", payment.getCashier() != null ? payment.getCashier().getUserName() : null);
        result.put("paymentTime", payment.getPaymentTime());
        result.put("discount", payment.getDiscount());
        result.put("total", payment.getTotal());
        result.put("subtotal", subtotal);
        result.put("cancel", Boolean.TRUE.equals(payment.getCancel()));
        result.put("details", detailList);
        result.put("seats", seatList);
        result.put("users", userList);
        result.put("paymentTypes", paymentTypeList);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/payments/history/{paymentId}/discount")
    public ResponseEntity<Void> updateDiscount(@CookieValue(name = "storeId", required = false) Integer storeId,
                                               @PathVariable("paymentId") Integer paymentId,
                                               @RequestBody Map<String, Double> body) {
        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null || !payment.getStore().getStoreId().equals(storeId)) {
            return ResponseEntity.notFound().build();
        }
        Double discount = body.get("discount");
        payment.setDiscount(discount);
        
        // 税込み合計金額を計算
        List<PaymentDetail> details = paymentDetailRepository.findByPaymentPaymentId(paymentId);
        double totalWithTax = details.stream()
                .mapToDouble(pd -> {
                    double base = pd.getSubtotal() != null ? pd.getSubtotal() : 0;
                    double detailDiscount = pd.getDiscount() != null ? pd.getDiscount() : 0;
                    double netSubtotalWithoutTax = Math.max(base - detailDiscount, 0);
                    double taxRate = pd.getTaxRate() != null ? pd.getTaxRate().getRate() : 0;
                    return netSubtotalWithoutTax * (1 + taxRate);
                })
                .sum();
        
        payment.setTotal(totalWithTax - (discount != null ? discount : 0));
        paymentRepository.save(payment);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/payments/history/{paymentId}/edit")
    public ResponseEntity<Void> editPaymentHistory(
            @CookieValue(name = "storeId", required = false) Integer storeId,
            @PathVariable("paymentId") Integer paymentId,
            @RequestBody PaymentHistoryUpdateRequest req) {

        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null || !payment.getStore().getStoreId().equals(storeId)) {
            return ResponseEntity.notFound().build();
        }

        if (req.getDiscount() != null) {
            payment.setDiscount(req.getDiscount());
        }

        if (req.getPaymentTypeId() != null) {
            PaymentType type = paymentTypeRepository.findById(req.getPaymentTypeId()).orElse(null);
            payment.setPaymentType(type);
        }

        if (req.getCashierId() != null) {
            User user = userRepository.findById(req.getCashierId()).orElse(null);
            payment.setCashier(user);
        }

        if (req.getSeatId() != null) {
            Seat seat = seatRepository.findById(req.getSeatId()).orElse(null);
            Visit visit = payment.getVisit();
            visit.setSeat(seat);
            visitRepository.save(visit);
        }

        if (req.getDetails() != null) {
            for (PaymentHistoryUpdateRequest.DetailUpdate d : req.getDetails()) {
                if (Boolean.TRUE.equals(d.getDelete())) {
                    paymentDetailRepository.deleteById(d.getPaymentDetailId());
                } else {
                    PaymentDetail detail = paymentDetailRepository.findById(d.getPaymentDetailId()).orElse(null);
                    if (detail != null) {
                        if (d.getQuantity() != null) {
                            detail.setQuantity(d.getQuantity());
                            if (detail.getMenu() != null && detail.getMenu().getPrice() != null) {
                                detail.setSubtotal(detail.getMenu().getPrice() * d.getQuantity());
                            }
                        }
                        if (d.getDiscount() != null) {
                            double baseSubtotal = detail.getSubtotal() != null ? detail.getSubtotal() : 0;
                            double requestedDiscount = d.getDiscount();
                            if (requestedDiscount < 0) {
                                requestedDiscount = 0;
                            }
                            if (requestedDiscount > baseSubtotal) {
                                requestedDiscount = baseSubtotal;
                            }
                            detail.setDiscount(requestedDiscount);
                        }
                        paymentDetailRepository.save(detail);
                    }
                }
            }
        }

        // recalc subtotal and total
        List<PaymentDetail> remaining = paymentDetailRepository.findByPaymentPaymentId(paymentId);
        double subtotal = remaining.stream()
                .mapToDouble(pd -> {
                    double base = pd.getSubtotal() != null ? pd.getSubtotal() : 0;
                    double detailDiscount = pd.getDiscount() != null ? pd.getDiscount() : 0;
                    double net = base - detailDiscount;
                    return net > 0 ? net : 0;
                })
                .sum();
        payment.setSubtotal(subtotal);
        
        // 税込み合計金額を計算
        double totalWithTax = remaining.stream()
                .mapToDouble(pd -> {
                    double base = pd.getSubtotal() != null ? pd.getSubtotal() : 0;
                    double detailDiscount = pd.getDiscount() != null ? pd.getDiscount() : 0;
                    double netSubtotalWithoutTax = Math.max(base - detailDiscount, 0);
                    double taxRate = pd.getTaxRate() != null ? pd.getTaxRate().getRate() : 0;
                    return netSubtotalWithoutTax * (1 + taxRate);
                })
                .sum();
        
        double discount = payment.getDiscount() != null ? payment.getDiscount() : 0;
        payment.setTotal(totalWithTax - discount);
        paymentRepository.save(payment);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/payments/history/{paymentId}/cancel")
    public ResponseEntity<Void> updatePaymentCancellation(
            @CookieValue(name = "storeId", required = false) Integer storeId,
            @PathVariable("paymentId") Integer paymentId,
            @RequestBody Map<String, Boolean> body) {

        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null || !payment.getStore().getStoreId().equals(storeId)) {
            return ResponseEntity.notFound().build();
        }

        Boolean cancel = body.get("cancel");
        if (cancel == null) {
            return ResponseEntity.badRequest().build();
        }

        payment.setCancel(cancel);
        paymentRepository.save(payment);
        return ResponseEntity.ok().build();
    }


    //会計確定: Payment および Visit の退店時刻を更新

    @Transactional
    @PostMapping("/payments/finalize")
    public ResponseEntity<Void> finalizePayment(@RequestBody PaymentFinalizeRequest req) {
    	//会計担当者を取得
    	User staff = userRepository.findById(req.getStaffId()).orElse(null);
    	
        // Payment を取得
        Payment payment = paymentRepository.findById(req.getPaymentId())
            .orElseThrow(() -> new IllegalArgumentException("Invalid paymentId"));

        // PaymentType を ID から取得してセット
        if (req.getPaymentTypeId() != null) {
            PaymentType type = paymentTypeRepository.findById(req.getPaymentTypeId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid paymentTypeId"));
            payment.setPaymentType(type);
        }

        // その他の値を設定
        payment.setSubtotal(req.getSubtotal());
        payment.setDiscount(req.getDiscount());
        payment.setDiscountReason(req.getDiscountReason());
        payment.setTotal(req.getTotal());
        payment.setPaymentTime(req.getPaymentTime());
        System.out.println(req.getPaymentTime());
        payment.setDeposit(req.getDeposit());
        payment.setCashier(staff);
        
        
        if (req.getDetails() != null && !req.getDetails().isEmpty()) {
            for (PaymentFinalizeRequest.PaymentDetailRequest detailReq : req.getDetails()) {
                // 既存のPaymentDetailレコードを更新する場合
                // detailReq.getPaymentDetailId() が送られてくることを前提
                if (detailReq.getPaymentDetailId() != null) {
                    PaymentDetail existingDetail = paymentDetailRepository.findById(detailReq.getPaymentDetailId())
                            .orElseThrow(() -> new IllegalArgumentException("Invalid paymentDetailId: " + detailReq.getPaymentDetailId()));
                    existingDetail.setDiscount(detailReq.getDiscountAmount());
                    // 必要であれば、quantityなども更新
                    paymentDetailRepository.save(existingDetail); // 更新を保存
                }
                // もし新しいPaymentDetailを作成する場合は別途ロジックが必要
            }
        }

        //  Visit 退店時刻を設定
        Visit visit = payment.getVisit();
        if(req.getPeople() != null) {
            visit.setNumberOfPeople(req.getPeople());
        }
        visit.setLeaveTime(req.getPaymentTime());
        System.out.println(req.getPaymentTime());
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "LEAVE");
        payload.put("seatId", visit.getSeat().getSeatId()); // 離席した座席のIDもペイロードに含める
        messagingTemplate.convertAndSend("/topic/seats/" + visit.getSeat().getSeatId(), payload); // 修正後のpayloadを送信



        // 保存
        paymentRepository.save(payment);
        visitRepository.save(visit);
        return ResponseEntity.ok().build();
    }
}
