package com.order.dto;

import lombok.Data;

@Data
public class SeatRequestDto {
    public int seatGroupId;
    public int storeId;
    public String seatName;
    public int maxCapacity;
}
