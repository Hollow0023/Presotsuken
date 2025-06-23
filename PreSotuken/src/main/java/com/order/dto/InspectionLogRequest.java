package com.order.dto;

import lombok.Data;

@Data
public class InspectionLogRequest {
    private Integer userId;      // 点検実施者
    private Integer yen10000;
    private Integer yen5000;
    private Integer yen1000;
    private Integer yen500;
    private Integer yen100;
    private Integer yen50;
    private Integer yen10;
    private Integer yen5;
    private Integer yen1;
}
