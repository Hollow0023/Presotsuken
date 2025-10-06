package com.order.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.order.dto.ReceiptIssueRequest;
import com.order.dto.ReceiptResponse;
import com.order.entity.Payment;
import com.order.entity.PaymentDetail;
import com.order.entity.Receipt;
import com.order.entity.User;
import com.order.repository.PaymentDetailRepository;
import com.order.repository.PaymentRepository;
import com.order.repository.ReceiptRepository;
import com.order.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReceiptService {
    
    private final ReceiptRepository receiptRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentDetailRepository paymentDetailRepository;
    private final UserRepository userRepository;
    
    // Receipt number counter per day
    private static final Map<String, AtomicInteger> dailyCounters = new HashMap<>();
    
    /**
     * Issue a new receipt
     */
    @Transactional
    public ReceiptResponse issueReceipt(ReceiptIssueRequest request) {
        log.info("Issuing receipt for payment: {}, mode: {}, amount: {}", 
                request.getPaymentId(), request.getMode(), request.getAmount());
        
        // Check idempotency
        if (request.getIdempotencyKey() != null) {
            Optional<Receipt> existing = receiptRepository.findByIdempotencyKey(request.getIdempotencyKey());
            if (existing.isPresent()) {
                log.info("Duplicate request detected with idempotency key: {}", request.getIdempotencyKey());
                return convertToResponse(existing.get());
            }
        }
        
        // Get payment
        Payment payment = paymentRepository.findById(request.getPaymentId())
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + request.getPaymentId()));
        
        // Get user
        User issuer = userRepository.findById(request.getIssuedByUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + request.getIssuedByUserId()));
        
        // Get payment details to calculate tax amounts
        List<PaymentDetail> details = paymentDetailRepository.findByPaymentPaymentId(payment.getPaymentId());
        
        // Calculate tax amounts from payment details
        Map<String, BigDecimal> taxAmounts = calculateTaxAmounts(details);
        BigDecimal total10 = taxAmounts.get("total10");
        BigDecimal total8 = taxAmounts.get("total8");
        
        // Calculate remaining amounts
        List<Receipt> existingReceipts = receiptRepository.findByPaymentPaymentIdOrderByIssuedAtDesc(payment.getPaymentId());
        Map<String, BigDecimal> remaining = calculateRemainingAmounts(total10, total8, existingReceipts);
        
        BigDecimal remaining10 = remaining.get("remaining10");
        BigDecimal remaining8 = remaining.get("remaining8");
        
        // Determine issue amount
        BigDecimal issueAmount;
        if ("FULL".equals(request.getMode())) {
            issueAmount = remaining10.add(remaining8);
        } else {
            issueAmount = request.getAmount();
            if (issueAmount.compareTo(remaining10.add(remaining8)) > 0) {
                throw new IllegalArgumentException("Issue amount exceeds remaining amount");
            }
        }
        
        // Allocate amount by tax rate
        Map<String, BigDecimal> allocated = allocateAmountByTaxRate(issueAmount, remaining10, remaining8);
        
        // Calculate net amounts and tax amounts
        BigDecimal allocated10 = allocated.get("amount10");
        BigDecimal allocated8 = allocated.get("amount8");
        
        Map<String, BigDecimal> breakdown10 = calculateTaxBreakdown(allocated10, new BigDecimal("1.10"));
        Map<String, BigDecimal> breakdown8 = calculateTaxBreakdown(allocated8, new BigDecimal("1.08"));
        
        BigDecimal net10 = breakdown10.get("net");
        BigDecimal tax10 = breakdown10.get("tax");
        BigDecimal net8 = breakdown8.get("net");
        BigDecimal tax8 = breakdown8.get("tax");
        
        // Adjust for rounding errors
        BigDecimal calculatedTotal = net10.add(tax10).add(net8).add(tax8);
        BigDecimal diff = issueAmount.subtract(calculatedTotal);
        
        if (diff.abs().compareTo(new BigDecimal("0.01")) <= 0 && diff.compareTo(BigDecimal.ZERO) != 0) {
            // Adjust tax10 first, then tax8 if needed
            if (tax10.compareTo(BigDecimal.ZERO) > 0) {
                tax10 = tax10.add(diff);
            } else if (tax8.compareTo(BigDecimal.ZERO) > 0) {
                tax8 = tax8.add(diff);
            }
        }
        
        // Create receipt
        Receipt receipt = new Receipt();
        receipt.setPayment(payment);
        receipt.setNetAmount10(net10);
        receipt.setNetAmount8(net8);
        receipt.setTaxAmount10(tax10);
        receipt.setTaxAmount8(tax8);
        receipt.setTotalAmount(issueAmount);
        receipt.setIssuedBy(issuer);
        receipt.setIssuedAt(LocalDateTime.now());
        receipt.setReceiptNo(generateReceiptNo());
        receipt.setReprintCount(0);
        receipt.setVoided(false);
        receipt.setIdempotencyKey(request.getIdempotencyKey());
        
        receipt = receiptRepository.save(receipt);
        
        log.info("Receipt issued: {}", receipt.getReceiptNo());
        
        return convertToResponse(receipt);
    }
    
    /**
     * Reprint existing receipt
     */
    @Transactional
    public ReceiptResponse reprintReceipt(Integer receiptId, Integer reprintByUserId) {
        log.info("Reprinting receipt: {}", receiptId);
        
        Receipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new IllegalArgumentException("Receipt not found: " + receiptId));
        
        if (receipt.getVoided()) {
            throw new IllegalArgumentException("Cannot reprint voided receipt");
        }
        
        receipt.setReprintCount(receipt.getReprintCount() + 1);
        receipt = receiptRepository.save(receipt);
        
        log.info("Receipt reprinted: {}, count: {}", receipt.getReceiptNo(), receipt.getReprintCount());
        
        return convertToResponse(receipt);
    }
    
    /**
     * Void a receipt
     */
    @Transactional
    public void voidReceipt(Integer receiptId, Integer voidedByUserId) {
        log.info("Voiding receipt: {}", receiptId);
        
        Receipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new IllegalArgumentException("Receipt not found: " + receiptId));
        
        if (receipt.getVoided()) {
            throw new IllegalArgumentException("Receipt already voided");
        }
        
        User voidedBy = userRepository.findById(voidedByUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + voidedByUserId));
        
        receipt.setVoided(true);
        receipt.setVoidedAt(LocalDateTime.now());
        receipt.setVoidedBy(voidedBy);
        
        receiptRepository.save(receipt);
        
        log.info("Receipt voided: {}", receipt.getReceiptNo());
    }
    
    /**
     * Get receipts for a payment
     */
    public List<ReceiptResponse> getReceiptsForPayment(Integer paymentId) {
        List<Receipt> receipts = receiptRepository.findByPaymentPaymentIdOrderByIssuedAtDesc(paymentId);
        return receipts.stream().map(this::convertToResponse).toList();
    }
    
    /**
     * Calculate remaining amounts for a payment
     */
    public Map<String, BigDecimal> getRemainingAmounts(Integer paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));
        
        List<PaymentDetail> details = paymentDetailRepository.findByPaymentPaymentId(payment.getPaymentId());
        Map<String, BigDecimal> taxAmounts = calculateTaxAmounts(details);
        
        List<Receipt> existingReceipts = receiptRepository.findByPaymentPaymentIdOrderByIssuedAtDesc(paymentId);
        
        return calculateRemainingAmounts(taxAmounts.get("total10"), taxAmounts.get("total8"), existingReceipts);
    }
    
    // Helper methods
    
    private Map<String, BigDecimal> calculateTaxAmounts(List<PaymentDetail> details) {
        BigDecimal total10 = BigDecimal.ZERO;
        BigDecimal total8 = BigDecimal.ZERO;
        
        for (PaymentDetail detail : details) {
            BigDecimal rate = BigDecimal.valueOf(detail.getTaxRate().getRate());
            BigDecimal subtotal = BigDecimal.valueOf(detail.getSubtotal());
            
            if (rate.compareTo(new BigDecimal("10")) == 0) {
                total10 = total10.add(subtotal);
            } else if (rate.compareTo(new BigDecimal("8")) == 0) {
                total8 = total8.add(subtotal);
            }
        }
        
        Map<String, BigDecimal> result = new HashMap<>();
        result.put("total10", total10);
        result.put("total8", total8);
        return result;
    }
    
    private Map<String, BigDecimal> calculateRemainingAmounts(BigDecimal total10, BigDecimal total8, List<Receipt> existingReceipts) {
        BigDecimal remaining10 = total10;
        BigDecimal remaining8 = total8;
        
        for (Receipt receipt : existingReceipts) {
            if (!receipt.getVoided()) {
                BigDecimal issued10 = receipt.getNetAmount10().add(receipt.getTaxAmount10());
                BigDecimal issued8 = receipt.getNetAmount8().add(receipt.getTaxAmount8());
                remaining10 = remaining10.subtract(issued10);
                remaining8 = remaining8.subtract(issued8);
            }
        }
        
        Map<String, BigDecimal> result = new HashMap<>();
        result.put("remaining10", remaining10.max(BigDecimal.ZERO));
        result.put("remaining8", remaining8.max(BigDecimal.ZERO));
        return result;
    }
    
    private Map<String, BigDecimal> allocateAmountByTaxRate(BigDecimal amount, BigDecimal remaining10, BigDecimal remaining8) {
        BigDecimal totalRemaining = remaining10.add(remaining8);
        
        if (totalRemaining.compareTo(BigDecimal.ZERO) == 0) {
            Map<String, BigDecimal> result = new HashMap<>();
            result.put("amount10", BigDecimal.ZERO);
            result.put("amount8", BigDecimal.ZERO);
            return result;
        }
        
        // Calculate proportions
        BigDecimal p10 = remaining10.divide(totalRemaining, 10, RoundingMode.HALF_UP);
        BigDecimal p8 = remaining8.divide(totalRemaining, 10, RoundingMode.HALF_UP);
        
        // Allocate amounts
        BigDecimal amount10 = amount.multiply(p10).setScale(0, RoundingMode.HALF_UP);
        BigDecimal amount8 = amount.subtract(amount10);
        
        Map<String, BigDecimal> result = new HashMap<>();
        result.put("amount10", amount10);
        result.put("amount8", amount8);
        return result;
    }
    
    private Map<String, BigDecimal> calculateTaxBreakdown(BigDecimal totalWithTax, BigDecimal taxRate) {
        if (totalWithTax.compareTo(BigDecimal.ZERO) == 0) {
            Map<String, BigDecimal> result = new HashMap<>();
            result.put("net", BigDecimal.ZERO);
            result.put("tax", BigDecimal.ZERO);
            return result;
        }
        
        BigDecimal net = totalWithTax.divide(taxRate, 0, RoundingMode.HALF_UP);
        BigDecimal tax = totalWithTax.subtract(net);
        
        Map<String, BigDecimal> result = new HashMap<>();
        result.put("net", net);
        result.put("tax", tax);
        return result;
    }
    
    private String generateReceiptNo() {
        LocalDateTime now = LocalDateTime.now();
        String dateStr = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        
        // Get or create counter for today
        AtomicInteger counter = dailyCounters.computeIfAbsent(dateStr, k -> new AtomicInteger(0));
        int sequence = counter.incrementAndGet();
        
        return String.format("%s-%04d", dateStr, sequence);
    }
    
    private ReceiptResponse convertToResponse(Receipt receipt) {
        ReceiptResponse response = new ReceiptResponse();
        response.setReceiptId(receipt.getReceiptId());
        response.setPaymentId(receipt.getPayment().getPaymentId());
        response.setNetAmount10(receipt.getNetAmount10());
        response.setNetAmount8(receipt.getNetAmount8());
        response.setTaxAmount10(receipt.getTaxAmount10());
        response.setTaxAmount8(receipt.getTaxAmount8());
        response.setTotalAmount(receipt.getTotalAmount());
        response.setIssuedByUserId(receipt.getIssuedBy().getUserId());
        response.setIssuedByUserName(receipt.getIssuedBy().getUserName());
        response.setIssuedAt(receipt.getIssuedAt());
        response.setReceiptNo(receipt.getReceiptNo());
        response.setReprintCount(receipt.getReprintCount());
        response.setVoided(receipt.getVoided());
        response.setVoidedAt(receipt.getVoidedAt());
        if (receipt.getVoidedBy() != null) {
            response.setVoidedByUserId(receipt.getVoidedBy().getUserId());
            response.setVoidedByUserName(receipt.getVoidedBy().getUserName());
        }
        return response;
    }
}
