package com.order.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class Menu {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer menuId;

    @ManyToOne
    @JoinColumn(name = "store_id")
    private Store store;

    @ManyToOne
    @JoinColumn(name = "tax_rate_id")
    private TaxRate taxRate;

    @ManyToOne
    @JoinColumn(name = "menu_group_id")
    private MenuGroup menuGroup;
    
    @ManyToOne
    @JoinColumn(name = "time_slot_id")
    private MenuTimeSlot timeSlot;
    
    @Column(name = "is_plan_starter") // DBのカラム名とマッピング
    private Boolean isPlanStarter; // 追加！

    @Column(name = "plan_id") // DBのカラム名とマッピング
    private Integer planId; // 追加！ (Planエンティティへの参照ID)

    private String menuName;
    private String menuImage;
    private Double price;
    private String menuDescription;
    private Boolean isSoldOut;
    private String receiptLabel;
    
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}