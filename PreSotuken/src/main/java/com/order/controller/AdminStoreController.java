package com.order.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.order.entity.Store;
import com.order.service.StoreService;

import lombok.RequiredArgsConstructor;

/**
 * 店舗管理に関する管理者機能を提供するコントローラ
 * 店舗情報の表示、編集を担当します
 */
@Controller
@RequestMapping("/admin/store")
@RequiredArgsConstructor
public class AdminStoreController {

    private final StoreService storeService;

    /**
     * 店舗編集フォームを表示します
     * 
     * @param storeId 店舗ID（Cookieから取得）
     * @param model ビューに渡すモデル
     * @return 店舗編集画面のテンプレート名
     */
    @GetMapping("/edit")
    public String showEditForm(
            @CookieValue(name = "storeId", required = false) Integer storeId,
            Model model) {

        if (storeId == null) {
            model.addAttribute("store", new Store());
            model.addAttribute("errorMessage", "店舗IDがCookieから見つかりませんでした。");
            return "storeEdit";
        }

        storeService.getStoreById(storeId).ifPresentOrElse(
            store -> model.addAttribute("store", store),
            () -> {
                model.addAttribute("store", new Store());
                model.addAttribute("errorMessage", "指定された店舗（ID: " + storeId + "）が見つかりませんでした。");
            }
        );
        return "storeEdit";
    }

    /**
     * 店舗情報を更新します
     * 
     * @param store 更新する店舗情報
     * @param redirectAttributes リダイレクト先に渡す属性
     * @return リダイレクト先URL
     */
    @PostMapping("/edit")
    public String updateStore(@ModelAttribute Store store, RedirectAttributes redirectAttributes) {
        if (store.getStoreId() == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "店舗IDが指定されていません。");
            return "redirect:/admin/store/edit";
        }

        try {
            storeService.updateStore(store);
            redirectAttributes.addFlashAttribute("successMessage", "店舗情報を更新しました！");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "店舗情報の更新に失敗しました。");
        }
        
        return "redirect:/admin/store/edit";
    }
}