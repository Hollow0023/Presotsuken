package com.order.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.order.entity.PrinterConfig;
import com.order.repository.PrinterConfigRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PrinterConfigService {

    private final PrinterConfigRepository printerConfigRepository;

    // 店舗IDに紐づくプリンタを全件取得
    public List<PrinterConfig> findByStoreId(Integer storeId) {
        return printerConfigRepository.findByStoreId(storeId);
    }

    // IDで1件取得
    public Optional<PrinterConfig> findById(Integer printerId) {
        return printerConfigRepository.findById(printerId);
    }

    // 保存（新規登録と更新の両方）
    @Transactional
    public void save(PrinterConfig printerConfig) {
        printerConfigRepository.save(printerConfig);
    }

    // 削除
    @Transactional
    public void deleteById(Integer printerId) {
        printerConfigRepository.deleteById(printerId);
    }
    
    //レシート印刷用プリンター保存
    public void updateReceiptPrinterForStore(Integer storeId, Integer selectedPrinterId) {
        List<PrinterConfig> printers = printerConfigRepository.findByStoreId(storeId);
        for (PrinterConfig printer : printers) {
            printer.setReceiptOutput(printer.getPrinterId().equals(selectedPrinterId));
        }
        printerConfigRepository.saveAll(printers);
    }

}