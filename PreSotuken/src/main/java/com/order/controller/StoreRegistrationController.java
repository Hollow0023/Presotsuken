package com.order.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.order.entity.Store;
import com.order.service.StoreService;

import lombok.RequiredArgsConstructor;

/**
 * 店舗登録に関するコントローラ
 * 新規店舗の登録を担当します
 */
@Controller
@RequestMapping("/store")
@RequiredArgsConstructor
public class StoreRegistrationController {

    private final StoreService storeService;

    /**
     * 店舗登録画面を表示します
     * 
     * @return 店舗登録画面のテンプレート名
     */
    @GetMapping("/register")
    public String showRegistrationForm() {
        return "store_register";
    }

    /**
     * 店舗を登録します
     * 
     * @param storeName 店舗名
     * @param model ビューに渡すモデル
     * @return 登録完了画面のテンプレート名
     */
    @PostMapping("/register")
    public String registerStore(@RequestParam String storeName, Model model) {
        try {
            Store createdStore = storeService.createStore(storeName);
            model.addAttribute("storeId", createdStore.getStoreId());
            model.addAttribute("storeName", createdStore.getStoreName());
            return "store_register_complete";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return "store_register";
        }
    }
}
