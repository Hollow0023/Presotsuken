package com.order.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CashTransactionRequest {

    @NotNull
    private Integer userId;

    @NotBlank
    private String type; // "IN" or "OUT"

    @NotNull
    @DecimalMin("0.01")
    private Double amount;

    private String reason; // 任意

}
