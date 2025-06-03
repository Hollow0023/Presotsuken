package com.order.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.order.entity.MenuTimeSlot;

public interface MenuTimeSlotRepository extends JpaRepository<MenuTimeSlot, Integer> {

    // storeIdに紐づく時間帯だけ取得
    List<MenuTimeSlot> findByStoreStoreId(int storeId);
    
}
