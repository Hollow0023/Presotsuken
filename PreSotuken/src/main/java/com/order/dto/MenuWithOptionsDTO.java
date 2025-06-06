package com.order.dto;

import java.util.List;

import com.order.entity.MenuGroup;
import com.order.entity.TaxRate;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter

public class MenuWithOptionsDTO {
    private int menuId;
    private String menuName;
    private Double price;
    private TaxRate taxRate;
    private MenuGroup menuGroup;
    private String menuImage;
    private String description;
    private int priceWithTax;

    private List<OptionGroupDTO> optionGroups;

}
