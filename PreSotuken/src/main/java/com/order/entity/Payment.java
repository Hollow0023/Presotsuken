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

@Getter
@Setter
@Entity
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer paymentId;

    @ManyToOne
    @JoinColumn(name = "store_id")
    private Store store;

    @ManyToOne
    @JoinColumn(name = "visit_id")
    private Visit visit;

    @Column(name = "visit_cancel", nullable = false)
    private Boolean visitCancel = false;

    @Column(name = "cancel", nullable = false)
    private Boolean cancel = false;

    private LocalDateTime paymentTime;
    private Double subtotal;
    private Double total;
    private Double discount;
    private String discountReason;
    private Double deposit;
    
    
    @ManyToOne
    @JoinColumn(name = "payment_type_id", referencedColumnName = "type_id")
    private PaymentType paymentType;

    @ManyToOne
    @JoinColumn(name = "cashier_id")
    private User cashier;
    
    // 個別会計機能用フィールド
    // 親会計ID: 元の会計を分割した場合、元の会計のIDを保持
    @ManyToOne
    @JoinColumn(name = "parent_payment_id")
    private Payment parentPayment;
    
    // 会計ステータス: PENDING(未完了), PARTIAL(部分完了), COMPLETED(完了)
    @Column(name = "payment_status", length = 20)
    private String paymentStatus = "COMPLETED";
    
    // 分割番号: 割り勘や個別会計で何番目の会計か (1から始まる)
    @Column(name = "split_number")
    private Integer splitNumber;
    
    // 総分割数: 割り勘での総分割数
    @Column(name = "total_splits")
    private Integer totalSplits;
}