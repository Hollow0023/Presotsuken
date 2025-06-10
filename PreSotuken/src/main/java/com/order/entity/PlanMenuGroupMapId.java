package com.order.entity; // PlanMenuGroupMapと同じパッケージにする

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PlanMenuGroupMapId implements Serializable {
	

    private Integer planId;
    private Integer menuGroupId;
    
    
}