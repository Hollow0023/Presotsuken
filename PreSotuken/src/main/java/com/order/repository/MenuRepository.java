package com.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.order.entity.Menu;

@Repository
public interface MenuRepository extends JpaRepository<Menu, Integer> {
    // Menu は主キーとして menuId を持っていることを想定
}
