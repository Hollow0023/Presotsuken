package com.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor; // ★ 追加
import lombok.Getter;
import lombok.NoArgsConstructor; // ★ 追加 (Spring Data JPAが内部でデフォルトコンストラクタを必要とすることがあるため)
import lombok.Setter;


@Getter
@Setter
@Entity
@Table(name = "plan_menu_group_map")
@IdClass(PlanMenuGroupMapId.class) // PlanMenuGroupMapIdをimportすることで解決
@NoArgsConstructor // ★ 追加
@AllArgsConstructor // ★ 追加
public class PlanMenuGroupMap {
	

    @Id
    @Column(name = "plan_id")
    private Integer planId;

    @Id
    @Column(name = "menu_group_id")
    private Integer menuGroupId;
}