package com.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ReceiptResponse {
    private Integer receiptId;
    private Integer paymentId;
    private BigDecimal netAmount10;
    private BigDecimal netAmount8;
    private BigDecimal taxAmount10;
    private BigDecimal taxAmount8;
    private BigDecimal totalAmount;
    private Integer issuedByUserId;
    private String issuedByUserName;
    private LocalDateTime issuedAt;
    private String receiptNo;
    private Integer reprintCount;
    private Boolean voided;
    private LocalDateTime voidedAt;
    private Integer voidedByUserId;
    private String voidedByUserName;
}
