package com.order.dto;

import java.util.List;

public class PaymentHistoryUpdateRequest {
    private Integer seatId;
    private Integer paymentTypeId;
    private Integer cashierId;
    private Double discount;
    private List<DetailUpdate> details;

    public Integer getSeatId() {
        return seatId;
    }

    public void setSeatId(Integer seatId) {
        this.seatId = seatId;
    }

    public Integer getPaymentTypeId() {
        return paymentTypeId;
    }

    public void setPaymentTypeId(Integer paymentTypeId) {
        this.paymentTypeId = paymentTypeId;
    }

    public Integer getCashierId() {
        return cashierId;
    }

    public void setCashierId(Integer cashierId) {
        this.cashierId = cashierId;
    }

    public Double getDiscount() {
        return discount;
    }

    public void setDiscount(Double discount) {
        this.discount = discount;
    }

    public List<DetailUpdate> getDetails() {
        return details;
    }

    public void setDetails(List<DetailUpdate> details) {
        this.details = details;
    }

    public static class DetailUpdate {
        private Integer paymentDetailId;
        private Integer quantity;
        private Boolean delete;

        public Integer getPaymentDetailId() {
            return paymentDetailId;
        }

        public void setPaymentDetailId(Integer paymentDetailId) {
            this.paymentDetailId = paymentDetailId;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }

        public Boolean getDelete() {
            return delete;
        }

        public void setDelete(Boolean delete) {
            this.delete = delete;
        }
    }
}
