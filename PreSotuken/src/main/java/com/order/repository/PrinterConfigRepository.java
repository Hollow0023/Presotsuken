package com.order.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.order.entity.PrinterConfig;

public interface PrinterConfigRepository extends JpaRepository<PrinterConfig, Integer> {
    List<PrinterConfig> findByStoreId(Integer storeId);
//    List<PrinterConfig> findByReceiptOutputTrue();
    PrinterConfig findByStoreIdAndReceiptOutput(Integer storeId, boolean isReceiptOutput);
    @Modifying(clearAutomatically = true) // (1) これが更新クエリであることを示すアノテーション
    @Query("UPDATE PrinterConfig p SET p.accountPrinter = false WHERE p.storeId = :storeId") // (2) 実行するJPQLクエリ
    void resetAccountPrinterForStore(@Param("storeId") Integer storeId); // (3) クエリ内の:storeIdにメソッドの引数を渡す


    /**
     * 参考：レシートプリンタ用のリセットメソッドも同様に作れるよ
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE PrinterConfig p SET p.receiptOutput = false WHERE p.storeId = :storeId")
    void resetReceiptOutputForStore(@Param("storeId") Integer storeId);
}
