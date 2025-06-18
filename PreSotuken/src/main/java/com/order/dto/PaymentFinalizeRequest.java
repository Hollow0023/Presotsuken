// com.order.dto.PaymentFinalizeRequest.java (例)
package com.order.dto;

import java.time.LocalDateTime;
import java.util.List; // List をインポート

import lombok.Data; // Lombokを使っている前提

@Data
public class PaymentFinalizeRequest {
    private Integer paymentId;
    private Double subtotal; // 全体の小計 (税込み、商品ごとの割引と全体割引適用前)
    private Double discount; // 全体割引額
    private String discountReason;
    private Double total;
    private LocalDateTime paymentTime;
    private Integer paymentTypeId;
    private Double deposit;
    private Integer staffId;
    private Integer people;

    // ★ここを追加！商品ごとの詳細と割引額を受け取るためのネストされたDTO
    private List<PaymentDetailRequest> details;

    // ネストされたDTO (PaymentDetailRequest) も定義するよ
    @Data
    public static class PaymentDetailRequest {
        private Integer paymentDetailId; // 既存のPaymentDetailを更新する場合に必要
        private String menuName; // 表示用
        private Double price; // 表示用
        private Integer quantity; // 表示用
        private Double taxRate; // 表示用
        private Double subtotal; // 元の小計 (税込み、割引前)
        private Double discountAmount; // ★この商品ごとの割引額
        // 必要に応じて menuId, taxRateId など、バックエンドで必要なIDも追加
        private Long menuId; // データベース更新に必要になる可能性
        private Long taxRateId; // データベース更新に必要になる可能性
    }
}