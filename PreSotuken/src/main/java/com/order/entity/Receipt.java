package com.order.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "receipt")
public class Receipt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "receipt_id")
    private Integer receiptId;

    @ManyToOne
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "net_amount_10", nullable = false, precision = 10, scale = 2)
    private BigDecimal netAmount10 = BigDecimal.ZERO;

    @Column(name = "net_amount_8", nullable = false, precision = 10, scale = 2)
    private BigDecimal netAmount8 = BigDecimal.ZERO;

    @Column(name = "tax_amount_10", nullable = false, precision = 10, scale = 2)
    private BigDecimal taxAmount10 = BigDecimal.ZERO;

    @Column(name = "tax_amount_8", nullable = false, precision = 10, scale = 2)
    private BigDecimal taxAmount8 = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @ManyToOne
    @JoinColumn(name = "issued_by", nullable = false)
    private User issuedBy;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "receipt_no", nullable = false, unique = true, length = 50)
    private String receiptNo;

    @Column(name = "reprint_count", nullable = false)
    private Integer reprintCount = 0;

    @Column(name = "voided", nullable = false)
    private Boolean voided = false;

    @Column(name = "voided_at")
    private LocalDateTime voidedAt;

    @ManyToOne
    @JoinColumn(name = "voided_by")
    private User voidedBy;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;
}
