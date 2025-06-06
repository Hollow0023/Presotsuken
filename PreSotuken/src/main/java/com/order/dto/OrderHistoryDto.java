package com.order.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderHistoryDto {
    private String menuName;
    private int quantity;
    private int subtotal;
    private Double price;      // メニューの単価
    private Double taxRate;     // 税率（例：0.1）


}
