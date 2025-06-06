package com.order.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "payment_detail_option")
public class PaymentDetailOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    private int paymentDetailId;

    private int optionItemId;

    // Getter / Setter
}

