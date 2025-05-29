package com.order.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.order.entity.User;

public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByUserId(Integer userId);
    List<User> findByStore_StoreId(Integer storeId);
}
