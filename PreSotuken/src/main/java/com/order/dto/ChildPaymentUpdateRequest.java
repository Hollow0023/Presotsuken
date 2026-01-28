package com.order.dto;

import lombok.Data;

/**
 * 子会計（割り勘会計の詳細）の更新リクエスト
 */
@Data
public class ChildPaymentUpdateRequest {
    private Integer paymentTypeId; // 支払い方法
    private Double amount;         // 支払い金額
}
