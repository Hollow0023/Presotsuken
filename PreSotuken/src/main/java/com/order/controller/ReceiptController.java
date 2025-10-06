package com.order.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.order.dto.ReceiptIssueRequest;
import com.order.dto.ReceiptResponse;
import com.order.service.ReceiptService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequiredArgsConstructor
@RequestMapping("/receipts")
@Slf4j
public class ReceiptController {
    
    private final ReceiptService receiptService;
    
    /**
     * Issue a new receipt
     */
    @PostMapping("/issue")
    public ResponseEntity<ReceiptResponse> issueReceipt(@RequestBody ReceiptIssueRequest request) {
        try {
            ReceiptResponse response = receiptService.issueReceipt(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Failed to issue receipt: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Reprint a receipt
     */
    @PostMapping("/{receiptId}/reprint")
    public ResponseEntity<ReceiptResponse> reprintReceipt(
            @PathVariable Integer receiptId,
            @RequestBody Map<String, Integer> body) {
        try {
            Integer userId = body.get("userId");
            ReceiptResponse response = receiptService.reprintReceipt(receiptId, userId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Failed to reprint receipt: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Void a receipt
     */
    @PostMapping("/{receiptId}/void")
    public ResponseEntity<Void> voidReceipt(
            @PathVariable Integer receiptId,
            @RequestBody Map<String, Integer> body) {
        try {
            Integer userId = body.get("userId");
            receiptService.voidReceipt(receiptId, userId);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            log.error("Failed to void receipt: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Get receipts for a payment
     */
    @GetMapping("/payment/{paymentId}")
    public ResponseEntity<List<ReceiptResponse>> getReceiptsForPayment(@PathVariable Integer paymentId) {
        List<ReceiptResponse> receipts = receiptService.getReceiptsForPayment(paymentId);
        return ResponseEntity.ok(receipts);
    }
    
    /**
     * Get remaining amounts for a payment
     */
    @GetMapping("/payment/{paymentId}/remaining")
    public ResponseEntity<Map<String, Object>> getRemainingAmounts(@PathVariable Integer paymentId) {
        try {
            Map<String, java.math.BigDecimal> remaining = receiptService.getRemainingAmounts(paymentId);
            return ResponseEntity.ok(Map.of(
                "remaining10", remaining.get("remaining10"),
                "remaining8", remaining.get("remaining8"),
                "totalRemaining", remaining.get("remaining10").add(remaining.get("remaining8"))
            ));
        } catch (IllegalArgumentException e) {
            log.error("Failed to get remaining amounts: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
}
