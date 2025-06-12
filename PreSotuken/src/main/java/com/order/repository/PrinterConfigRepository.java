package com.order.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.order.entity.PrinterConfig;

public interface PrinterConfigRepository extends JpaRepository<PrinterConfig, Integer> {
    List<PrinterConfig> findByStoreId(Integer storeId);
//    List<PrinterConfig> findByReceiptOutputTrue();
    List<PrinterConfig> findByStoreIdAndReceiptOutput(Integer storeId, boolean isReceiptOutput);
}
