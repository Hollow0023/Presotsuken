package com.order.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter

public class OptionGroupDTO {
    private int optionGroupId;
    private String groupName;
    private List<OptionItemDTO> optionItems;  // ← この中に辛さの選択肢とかが入る

    // Getter / Setter
}
