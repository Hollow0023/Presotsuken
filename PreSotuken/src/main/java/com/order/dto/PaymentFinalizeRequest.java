package com.order.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class PaymentFinalizeRequest {
    private Integer paymentId;
    private Double subtotal;
    private Double discount;
    private String discountReason;
    private Double total;
    private LocalDateTime paymentTime;
    private Integer paymentTypeId;

}