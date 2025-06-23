package com.order.service;

import org.springframework.stereotype.Component;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

@Component
public class CookieUtil {

    public Integer getStoreIdFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if ("storeId".equals(cookie.getName())) {
                try {
                    return Integer.parseInt(cookie.getValue());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }
}
