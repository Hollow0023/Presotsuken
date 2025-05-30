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
public class TableLoginController {

    private final StoreRepository storeRepository;

    public TableLoginController(StoreRepository storeRepository) {
        this.storeRepository = storeRepository;
    }

    @GetMapping("/tableLogin")
    public String showTableLoginForm(HttpServletRequest request, Model model) {
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

        if (storeId != null && storeName != null) {
            final String cookieStoreName = storeName;
            Store found = storeRepository.findById(storeId)
                    .filter(s -> s.getStoreName().equals(cookieStoreName))
                    .orElse(null);

            if (found != null) {
                return "redirect:/visits/orderwait";  // 自動ログイン成功
            }
        }

        model.addAttribute("store", new Store());
        return "tableLogin";
    }

    @PostMapping("/tableLogin")
    public String handleTableLogin(@ModelAttribute Store store,
                                   HttpServletResponse response,
                                   Model model) {
        Store found = storeRepository.findById(store.getStoreId())
                .filter(s -> s.getStoreName().equals(store.getStoreName()))
                .orElse(null);

        if (found != null) {
            // storeIdとstoreNameをクッキーに保存（4ヶ月）
            Cookie idCookie = new Cookie("storeId", String.valueOf(found.getStoreId()));
            Cookie nameCookie = new Cookie("storeName", found.getStoreName());
            idCookie.setPath("/");
            nameCookie.setPath("/");
            idCookie.setMaxAge(60 * 60 * 24 * 120);  // 120日間
            nameCookie.setMaxAge(60 * 60 * 24 * 120);
            response.addCookie(idCookie);
            response.addCookie(nameCookie);

            return "redirect:/visits/orderwait";
        } else {
            model.addAttribute("error", "店舗情報が一致しません");
            return "tableLogin";
        }
    }
}
