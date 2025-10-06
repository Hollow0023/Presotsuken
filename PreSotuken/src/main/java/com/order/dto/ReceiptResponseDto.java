package com.order.dto;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 領収書レスポンスDTO
 */
@Data
public class ReceiptResponseDto {
    private Integer receiptId;
    private Integer paymentId;
    private String receiptNo;
    private Double netAmount10;
    private Double taxAmount10;
    private Double netAmount8;
    private Double taxAmount8;
    private Double totalAmount;
    private String issuerName;
    private LocalDateTime issuedAt;
    private Integer reprintCount;
    private Boolean voided;
    private LocalDateTime voidedAt;
    private String voidedByName;
}
