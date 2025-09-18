package com.order.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import lombok.RequiredArgsConstructor;

/**
 * 管理者機能の共通画面を提供するコントローラ
 * 具体的な機能は各専用コントローラに分離されています：
 * - 端末管理: AdminTerminalController
 * - 店舗管理: AdminStoreController  
 * - ロゴ管理: AdminLogoController
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {
    /**
     * 管理者のメイン画面を表示します
     * 
     * @return 管理者メイン画面のテンプレート名
     */
    @GetMapping
    public String showAdminPage() {
        return "admin/index";
    }
}