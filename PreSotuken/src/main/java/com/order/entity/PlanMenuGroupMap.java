package com.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@Entity
@Table(name = "plan_menu_group_map")
@IdClass(PlanMenuGroupMapId.class)
@NoArgsConstructor
@AllArgsConstructor
public class PlanMenuGroupMap {
	

    @Id
    @Column(name = "plan_id")
    private Integer planId;

    @Id
    @Column(name = "menu_group_id")
    private Integer menuGroupId;
}