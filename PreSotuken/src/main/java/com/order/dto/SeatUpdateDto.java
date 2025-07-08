package com.order.dto;

import lombok.Data;

@Data
public class SeatUpdateDto {
    public int seatId;
    public String seatName;
    public int maxCapacity;
}
