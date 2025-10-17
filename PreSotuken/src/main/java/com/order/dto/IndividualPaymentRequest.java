package com.order.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/**
 * 個別会計リクエスト
 */
@Data
public class IndividualPaymentRequest {
    private Integer paymentId;              // 元の会計ID
    private List<ItemSelection> items;      // 支払う商品と数量のリスト
    private Integer paymentTypeId;          // 支払い種別ID
    private Integer cashierId;              // 担当者ID
    private Double deposit;                 // 預かり金額
    private LocalDateTime paymentTime;      // 会計時刻
    private Double discount;                // この会計での割引額
    
    /**
     * 商品選択情報（商品IDと数量）
     */
    @Data
    public static class ItemSelection {
        private Integer paymentDetailId;    // 元の PaymentDetail ID
        private Integer quantity;           // 支払う数量
    }
}
