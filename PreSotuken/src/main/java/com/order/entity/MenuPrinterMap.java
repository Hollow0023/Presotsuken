package com.order.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "menu_printer_map")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MenuPrinterMap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id; // menu_printer_mapテーブルのPK

    @ManyToOne
    @JoinColumn(name = "menu_id", nullable = false)
    private Menu menu; // メニューエンティティへの参照

    @ManyToOne
    @JoinColumn(name = "printer_id", nullable = false)
    private PrinterConfig printer; // プリンターエンティティへの参照

    // コンビニエンスコンストラクタ（特定のMenuとPrinterConfigで作成する場合）
    public MenuPrinterMap(Menu menu, PrinterConfig printer) {
        this.menu = menu;
        this.printer = printer;
    }
}