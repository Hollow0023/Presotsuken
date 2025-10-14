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
    private List<Integer> paymentDetailIds; // 支払う商品のIDリスト
    private Integer paymentTypeId;          // 支払い種別ID
    private Integer cashierId;              // 担当者ID
    private Double deposit;                 // 預かり金額
    private LocalDateTime paymentTime;      // 会計時刻
    private Double discount;                // この会計での割引額
}
