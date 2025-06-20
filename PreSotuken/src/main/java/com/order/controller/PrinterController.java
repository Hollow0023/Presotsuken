package com.order.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.order.entity.PrinterConfig;
import com.order.service.PrinterConfigService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/printers")
public class PrinterController {

    private final PrinterConfigService printerConfigService;

    // プリンタ設定一覧表示とフォームの表示
    @GetMapping
    public String showPage(@CookieValue("storeId") Integer storeId, Model model,
                           @RequestParam(name = "editId", required = false) Integer editId) {

        // 一覧表示用に、店舗に紐づくプリンタを全件取得
        model.addAttribute("printers", printerConfigService.findByStoreId(storeId));

        // フォーム用のオブジェクトを準備
        if (editId != null) {
            // 編集の場合：IDでプリンタ情報を取得してフォームにセット
            PrinterConfig printerToEdit = printerConfigService.findById(editId)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid printer Id:" + editId));
            model.addAttribute("printerForm", printerToEdit);
        } else {
            // 新規登録の場合：空のオブジェクトをフォームにセット
            PrinterConfig newPrinter = new PrinterConfig();
            newPrinter.setStoreId(storeId); // storeIdをあらかじめセット
            model.addAttribute("printerForm", newPrinter);
        }

        return "printerEdit"; // ★ここを "admin/printerEdit" に変更！
    }

    // 保存処理（新規登録・更新）
    @PostMapping("/save")
    public String savePrinter(@ModelAttribute("printerForm") PrinterConfig printerConfig,
                              @CookieValue("storeId") Integer storeId,
                              RedirectAttributes redirectAttributes) {
        
        // storeIdが不正に書き換えられないように、Cookieの値を再設定
        printerConfig.setStoreId(storeId);
        
        printerConfigService.save(printerConfig);
        redirectAttributes.addFlashAttribute("message", "保存しました！");
        return "redirect:/admin/printers";
    }

    // 削除処理
    @GetMapping("/delete/{id}")
    public String deletePrinter(@PathVariable("id") Integer id, RedirectAttributes redirectAttributes) {
        printerConfigService.deleteById(id);
        redirectAttributes.addFlashAttribute("message", "削除しました！");
        return "redirect:/admin/printers";
    }
    
    //プリンター設定用PostMapping
    @PostMapping("/update-receipt")
    public String updateReceiptPrinter(@RequestParam("receiptPrinterId") Integer selectedPrinterId,
                                       @CookieValue("storeId") Integer storeId,
                                       RedirectAttributes redirectAttributes) {

        printerConfigService.updateReceiptPrinterForStore(storeId, selectedPrinterId);
        redirectAttributes.addFlashAttribute("message", "レシート出力プリンタを更新しました！");
        return "redirect:/admin/printers";
    }
    
    @PostMapping("/update-account")
    public String updateAccountPrinter(@RequestParam("accountPrinterId") Integer selectedPrinterId,
                                       @CookieValue("storeId") Integer storeId,
                                       RedirectAttributes redirectAttributes) {

        // Serviceに新しいメソッドを呼び出す（後でServiceも修正が必要だよ）
        printerConfigService.updateAccountPrinterForStore(storeId, selectedPrinterId);
        redirectAttributes.addFlashAttribute("message", "会計伝票出力プリンタを更新しました！");
        return "redirect:/admin/printers";
    }

}