package com.order.dto;

import lombok.Data;

/**
 * 領収書発行リクエストDTO
 */
@Data
public class ReceiptIssueRequest {
    private Integer paymentId;
    private String mode; // "FULL" (全額) または "AMOUNT" (金額指定)
    private Double amount; // 金額指定モードの場合の発行額
    private Integer userId; // 発行者ID
    private String idempotencyKey; // 二重発行防止用キー
}
