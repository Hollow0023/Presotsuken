package com.order.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "inspection_log")
@Data
public class InspectionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer inspectionId;

    @ManyToOne
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private LocalDateTime inspectionTime;

    private BigDecimal totalCashSales;     // 売上（現金）
    private BigDecimal actualCashAmount;   // 実際の現金
    private BigDecimal differenceAmount;   // 差額
}
