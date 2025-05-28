package com.order.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

@Controller
public class LogoutController {

    @GetMapping("/logout")
    public String logout(HttpServletResponse response) {
        // Cookieを無効化（storeId, storeNameを削除）
        Cookie storeIdCookie = new Cookie("storeId", null);
        storeIdCookie.setMaxAge(0); // 即時削除
        storeIdCookie.setPath("/");

        Cookie storeNameCookie = new Cookie("storeName", null);
        storeNameCookie.setMaxAge(0);
        storeNameCookie.setPath("/");

        response.addCookie(storeIdCookie);
        response.addCookie(storeNameCookie);

        return "redirect:/"; // ログイン画面へリダイレクト
    }
}
