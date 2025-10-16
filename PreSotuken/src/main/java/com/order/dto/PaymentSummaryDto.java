package com.order.dto;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 会計サマリDTO（領収書発行残高を含む）
 */
@Data
public class PaymentSummaryDto {
    private Integer paymentId;
    private LocalDateTime paymentTime;
    
    // 会計全体の金額
    private Double totalAmount;
    private Double subtotal;
    private Double discount;
    
    // 税率別の内訳（会計全体）
    private Double netAmount10; // 10%対象税抜
    private Double taxAmount10; // 10%税額
    private Double grossAmount10; // 10%税込
    
    private Double netAmount8; // 8%対象税抜
    private Double taxAmount8; // 8%税額
    private Double grossAmount8; // 8%税込
    
    // 領収書発行残高（未発行分）
    private Double remainingAmount10; // 10%残額
    private Double remainingAmount8; // 8%残額
    private Double remainingTotal; // 合計残額
}
