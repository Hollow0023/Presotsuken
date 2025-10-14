package com.order.dto;

import java.util.List;
import lombok.Data;

/**
 * 残り会計情報DTO
 */
@Data
public class RemainingPaymentDto {
    private Integer paymentId;
    private Double totalAmount;           // 元の会計の合計金額
    private Double paidAmount;            // すでに支払われた金額
    private Double remainingAmount;       // 残りの金額
    private List<PaymentDetailDto> unpaidDetails; // 未払いの商品リスト
    private List<ChildPaymentDto> childPayments;  // 既存の子会計リスト
    private Boolean isFullyPaid;          // 全額支払い済みか
    
    @Data
    public static class PaymentDetailDto {
        private Integer paymentDetailId;
        private String menuName;
        private Integer quantity;
        private Double price;
        private Double taxRate;
        private Double subtotal;
        private Double discount;
        private Double totalWithTax;
    }
    
    @Data
    public static class ChildPaymentDto {
        private Integer paymentId;
        private Integer splitNumber;
        private Double amount;
        private String paymentTypeName;
        private String cashierName;
    }
}
