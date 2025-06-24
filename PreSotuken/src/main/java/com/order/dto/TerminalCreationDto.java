package com.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TerminalCreationDto {
    private Integer seatId;
    private String ipAddress;
    private boolean admin;
    // storeIdはCookieから取得するのでDTOには含めないことが多い
}