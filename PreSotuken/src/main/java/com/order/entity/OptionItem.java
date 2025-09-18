package com.order.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Entity
@Getter
@Setter
@ToString
@Table(name = "option_item") // テーブル名と一致
public class OptionItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int optionItemId; // DBの option_item_id と一致

    private int optionGroupId; // DBの option_group_id と一致

    private String itemName; // DBの item_name に合わせたプロパティ名

    // Getter / Setter はLombokで自動生成されるので、明示的な記述は不要
}