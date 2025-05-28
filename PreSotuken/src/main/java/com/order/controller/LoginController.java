package com.order.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import com.order.entity.Store;
import com.order.repository.StoreRepository;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Controller
public class LoginController {

    private final StoreRepository storeRepository;

    public LoginController(StoreRepository storeRepository) {
        this.storeRepository = storeRepository;
    }

    @GetMapping("/")
    public String showLoginForm(HttpServletRequest request, HttpServletResponse response, Model model) {
        Cookie[] cookies = request.getCookies();
        Integer storeId = null;
        String storeName = null;

        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("storeId".equals(cookie.getName())) {
                    try {
                        storeId = Integer.parseInt(cookie.getValue());
                    } catch (NumberFormatException e) {
                        storeId = null;
                    }
                }
                if ("storeName".equals(cookie.getName())) {
                    storeName = cookie.getValue();
                }
            }
        }

        // 両方取得できていれば、自動ログインを試行
        if (storeId != null && storeName != null) {
        	final String cookieStoreName = storeName;
        	Store found = storeRepository.findById(storeId)
        	    .filter(s -> s.getStoreName().equals(cookieStoreName))
        	    .orElse(null);

            if (found != null) {
                return "redirect:/seats"; // クッキーが有効ならそのままログイン成功
            }
        }

        // 通常ログイン画面
        model.addAttribute("store", new Store());
        return "login";
    }


    @PostMapping("/login")
    public String login(@ModelAttribute Store store, HttpServletResponse response, Model model) {
        Store found = storeRepository.findById(store.getStoreId())
                .filter(s -> s.getStoreName().equals(store.getStoreName()))
                .orElse(null);

        if (found != null) {
            // クッキーを保存（1週間）
            Cookie idCookie = new Cookie("storeId", String.valueOf(found.getStoreId()));
            Cookie nameCookie = new Cookie("storeName", found.getStoreName());
            idCookie.setPath("/");
            nameCookie.setPath("/");
            idCookie.setMaxAge(60 * 60 * 24 * 7);  // 1週間
            nameCookie.setMaxAge(60 * 60 * 24 * 7);
            response.addCookie(idCookie);
            response.addCookie(nameCookie);

            return "redirect:/seats";
        } else {
            model.addAttribute("error", "店舗情報が一致しません");
            return "login";
        }
    }
}
