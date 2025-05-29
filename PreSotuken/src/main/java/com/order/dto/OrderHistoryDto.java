package com.order.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderHistoryDto {
    private String menuName;
    private int quantity;
    private int subtotal;

}
