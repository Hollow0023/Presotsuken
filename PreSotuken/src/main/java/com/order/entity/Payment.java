package com.order.entity;

import java.time.LocalDateTime;

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

    private LocalDateTime paymentTime;
    private Double subtotal;
    private Double total;
    private Double discount;
    private String discountReason;
    
    
    @ManyToOne
    @JoinColumn(name = "payment_type_id", referencedColumnName = "type_id")
    private PaymentType paymentType;

    @ManyToOne
    @JoinColumn(name = "cashier_id")
    private User cashier;
}