package com.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "printer_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PrinterConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "printer_id")
    private Integer printerId;

    @Column(name = "printer_name", nullable = false)
    private String printerName;

    @Column(name = "printer_ip", nullable = false)
    private String printerIp;

    @Column(name = "store_id", nullable = false)
    private Integer storeId;
    
    @Column(name = "receipt_output", nullable = false)
    private boolean receiptOutput;  // ← これがレシート出力フラグ！
    private boolean accountPrinter;

}
