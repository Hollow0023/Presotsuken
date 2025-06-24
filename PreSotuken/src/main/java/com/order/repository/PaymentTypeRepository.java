package com.order.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.order.entity.PaymentType;

@Repository
public interface PaymentTypeRepository extends JpaRepository<PaymentType, Integer> {
    List<PaymentType> findByStoreId(Integer storeId);
    List<PaymentType> findAllByOrderByTypeNameAsc();
}
