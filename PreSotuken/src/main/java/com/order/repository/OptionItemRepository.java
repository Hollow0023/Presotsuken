package com.order.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.order.entity.OptionItem;

@Repository
public interface OptionItemRepository extends JpaRepository<OptionItem, Integer> {
    List<OptionItem> findByOptionGroupId(int optionGroupId);
}
