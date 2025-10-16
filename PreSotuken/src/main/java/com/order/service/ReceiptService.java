package com.order.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.order.dto.PaymentSummaryDto;
import com.order.dto.ReceiptIssueRequest;
import com.order.dto.ReceiptResponseDto;
import com.order.entity.Payment;
import com.order.entity.PaymentDetail;
import com.order.entity.Receipt;
import com.order.entity.User;
import com.order.repository.PaymentDetailRepository;
import com.order.repository.PaymentRepository;
import com.order.repository.ReceiptRepository;
import com.order.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * 領収書サービス
 * 領収書の発行、再印字、取消、按分計算などを管理
 */
@Service
@RequiredArgsConstructor
public class ReceiptService {

    private final ReceiptRepository receiptRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentDetailRepository paymentDetailRepository;
    private final UserRepository userRepository;

    /**
     * 会計の税率別サマリと領収書発行残高を計算
     */
    public PaymentSummaryDto calculatePaymentSummary(Integer paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));

        List<PaymentDetail> details = paymentDetailRepository.findByPaymentPaymentId(paymentId);

        // 税率別に集計
        BigDecimal net10 = BigDecimal.ZERO;
        BigDecimal tax10 = BigDecimal.ZERO;
        BigDecimal net8 = BigDecimal.ZERO;
        BigDecimal tax8 = BigDecimal.ZERO;

        for (PaymentDetail detail : details) {
            // null チェック: taxRate が null の場合はスキップ
            if (detail.getTaxRate() == null) {
                continue;
            }
            
            BigDecimal unitPrice = BigDecimal.valueOf(detail.getMenu().getPrice());
            BigDecimal quantity = BigDecimal.valueOf(detail.getQuantity());
            BigDecimal taxRate = BigDecimal.valueOf(detail.getTaxRate().getRate());
            BigDecimal itemDiscount = detail.getDiscount() != null ? 
                BigDecimal.valueOf(detail.getDiscount()) : BigDecimal.ZERO;

            // 商品ごとの税抜金額
            BigDecimal itemNet = unitPrice.multiply(quantity);
            // 商品ごとの税額
            BigDecimal itemTax = itemNet.multiply(taxRate).setScale(0, RoundingMode.HALF_UP);
            
            // 割引適用後の金額を按分
            BigDecimal itemGross = itemNet.add(itemTax);
            if (itemDiscount.compareTo(BigDecimal.ZERO) > 0) {
                // 割引がある場合、税込金額から割引を引いて、税抜と税額を再計算
                BigDecimal discountedGross = itemGross.subtract(itemDiscount);
                BigDecimal divisor = BigDecimal.ONE.add(taxRate);
                BigDecimal discountedNet = discountedGross.divide(divisor, 0, RoundingMode.HALF_UP);
                BigDecimal discountedTax = discountedGross.subtract(discountedNet);
                
                itemNet = discountedNet;
                itemTax = discountedTax;
            }

            // 税率が0.10の場合
            if (taxRate.compareTo(BigDecimal.valueOf(0.10)) == 0) {
                net10 = net10.add(itemNet);
                tax10 = tax10.add(itemTax);
            }
            // 税率が0.08の場合
            else if (taxRate.compareTo(BigDecimal.valueOf(0.08)) == 0) {
                net8 = net8.add(itemNet);
                tax8 = tax8.add(itemTax);
            }
        }

        // 全体の割引を按分
        BigDecimal paymentDiscount = payment.getDiscount() != null ? 
            BigDecimal.valueOf(payment.getDiscount()) : BigDecimal.ZERO;
        
        BigDecimal gross10 = net10.add(tax10);
        BigDecimal gross8 = net8.add(tax8);
        BigDecimal totalGross = gross10.add(gross8);

        if (paymentDiscount.compareTo(BigDecimal.ZERO) > 0 && totalGross.compareTo(BigDecimal.ZERO) > 0) {
            // 割引を税込金額の比率で按分
            BigDecimal discountRatio10 = gross10.divide(totalGross, 10, RoundingMode.HALF_UP);
            BigDecimal discountRatio8 = gross8.divide(totalGross, 10, RoundingMode.HALF_UP);
            
            BigDecimal discount10 = paymentDiscount.multiply(discountRatio10).setScale(0, RoundingMode.HALF_UP);
            BigDecimal discount8 = paymentDiscount.subtract(discount10);

            // 割引後の税込金額を計算
            gross10 = gross10.subtract(discount10);
            gross8 = gross8.subtract(discount8);

            // 税抜と税額を再計算
            net10 = gross10.divide(BigDecimal.valueOf(1.10), 0, RoundingMode.HALF_UP);
            tax10 = gross10.subtract(net10);
            net8 = gross8.divide(BigDecimal.valueOf(1.08), 0, RoundingMode.HALF_UP);
            tax8 = gross8.subtract(net8);
        }

        // 既に発行された領収書の合計を計算
        List<Receipt> issuedReceipts = receiptRepository
            .findByPaymentPaymentIdAndVoidedFalseOrderByIssuedAtDesc(paymentId);
        
        BigDecimal issued10 = BigDecimal.ZERO;
        BigDecimal issued8 = BigDecimal.ZERO;

        for (Receipt receipt : issuedReceipts) {
            if (receipt.getNetAmount10() != null && receipt.getTaxAmount10() != null) {
                issued10 = issued10.add(BigDecimal.valueOf(receipt.getNetAmount10()))
                    .add(BigDecimal.valueOf(receipt.getTaxAmount10()));
            }
            if (receipt.getNetAmount8() != null && receipt.getTaxAmount8() != null) {
                issued8 = issued8.add(BigDecimal.valueOf(receipt.getNetAmount8()))
                    .add(BigDecimal.valueOf(receipt.getTaxAmount8()));
            }
        }

        // 残高を計算
        BigDecimal remaining10 = gross10.subtract(issued10);
        BigDecimal remaining8 = gross8.subtract(issued8);

        PaymentSummaryDto summary = new PaymentSummaryDto();
        summary.setPaymentId(paymentId);
        summary.setPaymentTime(payment.getPaymentTime());
        summary.setTotalAmount(payment.getTotal());
        summary.setSubtotal(payment.getSubtotal());
        summary.setDiscount(payment.getDiscount());
        summary.setNetAmount10(net10.doubleValue());
        summary.setTaxAmount10(tax10.doubleValue());
        summary.setGrossAmount10(gross10.doubleValue());
        summary.setNetAmount8(net8.doubleValue());
        summary.setTaxAmount8(tax8.doubleValue());
        summary.setGrossAmount8(gross8.doubleValue());
        summary.setRemainingAmount10(remaining10.doubleValue());
        summary.setRemainingAmount8(remaining8.doubleValue());
        summary.setRemainingTotal(remaining10.add(remaining8).doubleValue());

        return summary;
    }

    /**
     * 領収書を発行する
     */
    @Transactional
    public Receipt issueReceipt(ReceiptIssueRequest request) {
        // idempotencyKeyチェック（二重発行防止）
        if (request.getIdempotencyKey() != null) {
            Optional<Receipt> existing = receiptRepository.findByIdempotencyKey(request.getIdempotencyKey());
            if (existing.isPresent()) {
                return existing.get(); // 既に発行済み
            }
        }

        Payment payment = paymentRepository.findById(request.getPaymentId())
            .orElseThrow(() -> new IllegalArgumentException("Payment not found"));

        User issuer = userRepository.findById(request.getUserId())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        PaymentSummaryDto summary = calculatePaymentSummary(request.getPaymentId());

        Receipt receipt = new Receipt();
        receipt.setPayment(payment);
        receipt.setStore(payment.getStore());
        receipt.setIssuer(issuer);
        receipt.setIssuedAt(LocalDateTime.now());
        receipt.setIdempotencyKey(request.getIdempotencyKey());

        if ("FULL".equals(request.getMode())) {
            // 全額モード：残高全額を発行
            receipt.setNetAmount10(calculateNetAmount(summary.getRemainingAmount10(), 0.10));
            receipt.setTaxAmount10(calculateTaxAmount(summary.getRemainingAmount10(), 0.10));
            receipt.setNetAmount8(calculateNetAmount(summary.getRemainingAmount8(), 0.08));
            receipt.setTaxAmount8(calculateTaxAmount(summary.getRemainingAmount8(), 0.08));
        } else if ("AMOUNT".equals(request.getMode())) {
            // 金額指定モード：按分計算
            BigDecimal amount = BigDecimal.valueOf(request.getAmount());
            BigDecimal remaining10 = BigDecimal.valueOf(summary.getRemainingAmount10());
            BigDecimal remaining8 = BigDecimal.valueOf(summary.getRemainingAmount8());
            BigDecimal remainingTotal = remaining10.add(remaining8);

            if (amount.compareTo(remainingTotal) > 0) {
                throw new IllegalArgumentException("発行額が残高を超えています");
            }

            // 按分比率を計算
            BigDecimal ratio10 = BigDecimal.ZERO;
            BigDecimal ratio8 = BigDecimal.ZERO;
            
            if (remainingTotal.compareTo(BigDecimal.ZERO) > 0) {
                ratio10 = remaining10.divide(remainingTotal, 10, RoundingMode.HALF_UP);
                ratio8 = remaining8.divide(remainingTotal, 10, RoundingMode.HALF_UP);
            }

            // 按分して配分
            BigDecimal amount10 = amount.multiply(ratio10).setScale(0, RoundingMode.HALF_UP);
            BigDecimal amount8 = amount.subtract(amount10);

            receipt.setNetAmount10(calculateNetAmount(amount10.doubleValue(), 0.10));
            receipt.setTaxAmount10(calculateTaxAmount(amount10.doubleValue(), 0.10));
            receipt.setNetAmount8(calculateNetAmount(amount8.doubleValue(), 0.08));
            receipt.setTaxAmount8(calculateTaxAmount(amount8.doubleValue(), 0.08));
        }

        // 印字番号を生成（日付+通番）
        String receiptNo = generateReceiptNo(payment.getStore().getStoreId());
        receipt.setReceiptNo(receiptNo);

        return receiptRepository.save(receipt);
    }

    /**
     * 税込金額から税抜金額を計算
     */
    private Double calculateNetAmount(Double grossAmount, Double taxRate) {
        if (grossAmount == null || grossAmount == 0.0) {
            return 0.0;
        }
        BigDecimal gross = BigDecimal.valueOf(grossAmount);
        BigDecimal divisor = BigDecimal.ONE.add(BigDecimal.valueOf(taxRate));
        return gross.divide(divisor, 0, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * 税込金額から税額を計算
     */
    private Double calculateTaxAmount(Double grossAmount, Double taxRate) {
        if (grossAmount == null || grossAmount == 0.0) {
            return 0.0;
        }
        Double netAmount = calculateNetAmount(grossAmount, taxRate);
        return grossAmount - netAmount;
    }

    /**
     * 印字番号を生成
     */
    private String generateReceiptNo(Integer storeId) {
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        // 当日の発行枚数を取得して通番を生成
        // 簡易実装：ミリ秒タイムスタンプを使用
        String timestamp = String.valueOf(System.currentTimeMillis());
        return String.format("R%d-%s-%s", storeId, dateStr, timestamp.substring(timestamp.length() - 6));
    }

    /**
     * 領収書を再印字（reprintCountをインクリメント）
     */
    @Transactional
    public Receipt reprintReceipt(Integer receiptId) {
        Receipt receipt = receiptRepository.findById(receiptId)
            .orElseThrow(() -> new IllegalArgumentException("Receipt not found"));
        
        if (receipt.getVoided()) {
            throw new IllegalStateException("取消済みの領収書は再印字できません");
        }

        receipt.setReprintCount(receipt.getReprintCount() + 1);
        return receiptRepository.save(receipt);
    }

    /**
     * 領収書を取消
     */
    @Transactional
    public Receipt voidReceipt(Integer receiptId, Integer userId) {
        Receipt receipt = receiptRepository.findById(receiptId)
            .orElseThrow(() -> new IllegalArgumentException("Receipt not found"));

        if (receipt.getVoided()) {
            throw new IllegalStateException("既に取消済みです");
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        receipt.setVoided(true);
        receipt.setVoidedAt(LocalDateTime.now());
        receipt.setVoidedBy(user);

        return receiptRepository.save(receipt);
    }

    /**
     * 会計IDから領収書一覧を取得
     */
    public List<ReceiptResponseDto> getReceiptsByPaymentId(Integer paymentId) {
        List<Receipt> receipts = receiptRepository.findByPaymentPaymentIdOrderByIssuedAtDesc(paymentId);
        List<ReceiptResponseDto> dtos = new ArrayList<>();

        for (Receipt receipt : receipts) {
            ReceiptResponseDto dto = new ReceiptResponseDto();
            dto.setReceiptId(receipt.getReceiptId());
            dto.setPaymentId(receipt.getPayment().getPaymentId());
            dto.setReceiptNo(receipt.getReceiptNo());
            dto.setNetAmount10(receipt.getNetAmount10());
            dto.setTaxAmount10(receipt.getTaxAmount10());
            dto.setNetAmount8(receipt.getNetAmount8());
            dto.setTaxAmount8(receipt.getTaxAmount8());
            
            double total = 0.0;
            if (receipt.getNetAmount10() != null) total += receipt.getNetAmount10();
            if (receipt.getTaxAmount10() != null) total += receipt.getTaxAmount10();
            if (receipt.getNetAmount8() != null) total += receipt.getNetAmount8();
            if (receipt.getTaxAmount8() != null) total += receipt.getTaxAmount8();
            dto.setTotalAmount(total);
            
            dto.setIssuerName(receipt.getIssuer().getUserName());
            dto.setIssuedAt(receipt.getIssuedAt());
            dto.setReprintCount(receipt.getReprintCount());
            dto.setVoided(receipt.getVoided());
            dto.setVoidedAt(receipt.getVoidedAt());
            if (receipt.getVoidedBy() != null) {
                dto.setVoidedByName(receipt.getVoidedBy().getUserName());
            }

            dtos.add(dto);
        }

        return dtos;
    }
}
