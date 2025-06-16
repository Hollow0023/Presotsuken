package com.order.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.order.entity.MenuPrinterMap;
import com.order.entity.PrinterConfig;

public interface MenuPrinterMapRepository extends JpaRepository<MenuPrinterMap, Integer> {


    List<MenuPrinterMap> findByPrinter(PrinterConfig printer);
    
    void deleteByMenu_MenuId(Integer menuId);
//    List<MenuPrinterMap> findByMenu_MenuId(Integer menuId);
    MenuPrinterMap findFirstByMenu_MenuIdOrderByPrinter_PrinterIdAsc(Integer menuId);

    
}
