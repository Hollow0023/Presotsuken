package com.order.dto;

import java.time.LocalTime;

import lombok.Getter;
import lombok.Setter;

/**
 * メニュー時間帯のリクエストDTO
 */
@Getter
@Setter
public class MenuTimeSlotRequest {

    private Integer timeSlotId;
    private String name;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer storeId;
}
