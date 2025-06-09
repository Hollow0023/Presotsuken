package com.order.entity;

import com.fasterxml.jackson.annotation.JsonBackReference; // 追加

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn; // 追加
import jakarta.persistence.ManyToOne;  // 追加
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "menu_option")
public class MenuOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    // ★★★ Menuエンティティへの参照に変更 ★★★
    @ManyToOne
    @JoinColumn(name = "menu_id", referencedColumnName = "menuId", nullable = false) // menu_idカラムでMenuエンティティのmenuIdを参照
    @JsonBackReference // Menu側で@JsonManagedReferenceがあるため、こちらで無限ループを防ぐ
    private Menu menu; // Menuエンティティへの参照

    // private int menuId; // 削除

    private int optionGroupId;

    // Getter / Setter は Lombok で自動生成される
}