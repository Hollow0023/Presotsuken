package com.order.dto;

import java.util.List;

import lombok.Data;

@Data
public class PaymentHistoryUpdateRequest {
    private Integer seatId;
    private Integer paymentTypeId;
    private Integer cashierId;
    private Double discount;
    private List<DetailUpdate> details;

    @Data
    public static class DetailUpdate {
        private Integer paymentDetailId;
        private Integer quantity;
        private Double discount;
        private Boolean delete;
    }
}
