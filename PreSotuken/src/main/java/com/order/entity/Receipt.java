package com.order.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

/**
 * 領収書発行履歴エンティティ
 */
@Getter
@Setter
@Entity
public class Receipt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer receiptId;

    @ManyToOne
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @ManyToOne
    @JoinColumn(name = "store_id")
    private Store store;

    // 税抜金額（10%対象）
    @Column(name = "net_amount_10")
    private Double netAmount10;

    // 税額（10%対象）
    @Column(name = "tax_amount_10")
    private Double taxAmount10;

    // 税抜金額（8%対象）
    @Column(name = "net_amount_8")
    private Double netAmount8;

    // 税額（8%対象）
    @Column(name = "tax_amount_8")
    private Double taxAmount8;

    // 発行者
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User issuer;

    // 発行日時
    @Column(name = "issued_at")
    private LocalDateTime issuedAt;

    // 印字番号（日付＋通番など）
    @Column(name = "receipt_no", length = 50)
    private String receiptNo;

    // 再印字回数
    @Column(name = "reprint_count")
    private Integer reprintCount = 0;

    // 取消フラグ
    @Column(name = "voided")
    private Boolean voided = false;

    // 取消日時
    @Column(name = "voided_at")
    private LocalDateTime voidedAt;

    // 取消者
    @ManyToOne
    @JoinColumn(name = "voided_by_user_id")
    private User voidedBy;

    // idempotencyKey（二重発行防止用）
    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;
}
