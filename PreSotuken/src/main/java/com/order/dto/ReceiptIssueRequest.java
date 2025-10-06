package com.order.dto;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class ReceiptIssueRequest {
    private Integer paymentId;
    private String mode; // "FULL" or "AMOUNT"
    private BigDecimal amount; // for AMOUNT mode
    private Integer issuedByUserId;
    private String idempotencyKey;
}
