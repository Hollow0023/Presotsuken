package com.order.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn; // 追加
import jakarta.persistence.ManyToOne; // 追加
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "payment_detail_option")
public class PaymentDetailOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    // paymentDetailId を PaymentDetail エンティティへの参照に変更
    @ManyToOne
    @JoinColumn(name = "payment_detail_id") // DBのカラム名を指定
    private PaymentDetail paymentDetail;

    // optionItemId を OptionItem エンティティへの参照に変更
    @ManyToOne
    @JoinColumn(name = "option_item_id") // DBのカラム名を指定
    private OptionItem optionItem;

    // Getter / Setter はLombokで自動生成されるため、明示的な記述は不要
}