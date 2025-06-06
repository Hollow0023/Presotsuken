package com.order.controller;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.order.entity.Store;
import com.order.entity.User;
import com.order.repository.StoreRepository;
import com.order.repository.UserRepository;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor

public class LoginController {

    private final StoreRepository storeRepository;
    private final UserRepository userRepository;


    @GetMapping("/")
    public String showLoginForm(HttpServletRequest request, HttpServletResponse response, Model model) {
        Cookie[] cookies = request.getCookies();
        Integer storeId = null;
        String storeName = null;

        //cookieにstoreIDがある場合、IDから店舗名を求めて保存する
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
            // クッキーを保存（120日）
            Cookie idCookie = new Cookie("storeId", String.valueOf(found.getStoreId()));
            Cookie nameCookie = new Cookie("storeName", found.getStoreName());
            idCookie.setPath("/");
            nameCookie.setPath("/");
            idCookie.setMaxAge(60 * 60 * 24 * 120);  // 120日間
            nameCookie.setMaxAge(60 * 60 * 24 * 120);
            response.addCookie(idCookie);
            response.addCookie(nameCookie);

            return "redirect:/seats";
        } else {
            model.addAttribute("error", "店舗情報が一致しません");
            return "login";
        }
    }
    
    @GetMapping("/api/users/by-store")
    @ResponseBody
    public List<User> getUsersByStore(@RequestParam Integer storeId) {
        return userRepository.findByStore_StoreId(storeId);
    }
    
    @GetMapping("/api/stores/check")
    @ResponseBody
    public Map<String, Boolean> checkStore(@RequestParam Integer storeId, @RequestParam String storeName) {
        boolean valid = storeRepository.existsByStoreIdAndStoreName(storeId, storeName);
        return Map.of("valid", valid);
    }
}
