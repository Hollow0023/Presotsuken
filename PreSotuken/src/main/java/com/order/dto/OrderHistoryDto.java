package com.order.dto;

import java.util.List;

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
    private List<String> selectedOptionNames; // ★追加：選択されたオプションの名前リスト



}
