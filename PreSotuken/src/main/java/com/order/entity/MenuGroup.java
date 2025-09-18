package com.order.entity;

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
public class MenuGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer groupId;

    @ManyToOne
    @JoinColumn(name = "store_id")
    private Store store;
    
    @Column(name = "is_plan_target") // DBのカラム名とマッピング
    private Boolean isPlanTarget;

    @Column(name = "sort_order")
    private Integer sortOrder;

    private String groupName;
    private Boolean forAdminOnly;
}