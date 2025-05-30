package com.order.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.order.entity.TaxRate;

public interface TaxRateRepository extends JpaRepository<TaxRate, Integer> {
    Optional<TaxRate> findByTaxRateId(Integer taxRateId);
    List<TaxRate> findByStore_StoreId(Integer storeId);
}
