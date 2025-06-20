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
    public void save(PrinterConfig printer) {
        // まず、名前やIPアドレスなどの基本的な情報を保存・更新する
        // この時点でチェックボックスの状態も一旦DBに保存される
        printerConfigRepository.save(printer);

        // --- ここからが大事な部分 ---
        // もし「レシート出力用」にチェックが入っていたら…
        if (printer.isReceiptOutput()) {
            // …このプリンタが唯一のレシート出力用になるように、更新処理を呼び出す
            // (これにより、他のプリンタのフラグは自動でfalseになる)
            this.updateReceiptPrinterForStore(printer.getStoreId(), printer.getPrinterId());
        }

        // もし「会計伝票出力用」にチェックが入っていたら…
        if (printer.isAccountPrinter()) {
            // …このプリンタが唯一の会計伝票出力用になるように、更新処理を呼び出す
            this.updateAccountPrinterForStore(printer.getStoreId(), printer.getPrinterId());
        }
    }

    // 削除
    @Transactional
    public void deleteById(Integer printerId) {
        printerConfigRepository.deleteById(printerId);
    }
    
    @Transactional
    public void updateReceiptPrinterForStore(Integer storeId, Integer selectedPrinterId) {
        // 1. まず、この店舗のレシート用フラグを全部リセット
        printerConfigRepository.resetReceiptOutputForStore(storeId);

        // 2. 次に、選ばれたプリンタだけをONに設定
        printerConfigRepository.findById(selectedPrinterId).ifPresent(printer -> {
            if (printer.getStoreId().equals(storeId)) {
                printer.setReceiptOutput(true);
                printerConfigRepository.save(printer);
            }
        });
    }
    
    @Transactional
    public void updateAccountPrinterForStore(Integer storeId, Integer selectedPrinterId) {
        // 1. まず、指定された店舗のすべてのプリンタのaccountPrinterフラグをfalseにリセット
        printerConfigRepository.resetAccountPrinterForStore(storeId);
        
        // 2. 次に、選択されたプリンタのaccountPrinterフラグをtrueに設定
        printerConfigRepository.findById(selectedPrinterId).ifPresent(printer -> {
            // 念のため、プリンタが正しい店舗に属しているか確認
            if (printer.getStoreId().equals(storeId)) {
                printer.setAccountPrinter(true);
                printerConfigRepository.save(printer);
            }
        });
    }

}