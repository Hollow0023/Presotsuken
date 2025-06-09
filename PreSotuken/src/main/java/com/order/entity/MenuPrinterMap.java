package com.order.entity;

import com.fasterxml.jackson.annotation.JsonBackReference; // 追加

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
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "menu_id", nullable = false)
    @JsonBackReference // Menu側で@JsonManagedReferenceがあるため、こちらで無限ループを防ぐ
    private Menu menu;

    @ManyToOne
    @JoinColumn(name = "printer_id", nullable = false)
    private PrinterConfig printer;

    public MenuPrinterMap(Menu menu, PrinterConfig printer) {
        this.menu = menu;
        this.printer = printer;
    }
}