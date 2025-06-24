package com.order.interceptor;

import java.io.IOException;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class LoginCheckInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String uri = request.getRequestURI();

        // 除外対象
        List<String> excludePrefixes = List.of("/","/login", "/logout", "/css", "/js", "/images", "/favicon", "/fonts");
        for (String prefix : excludePrefixes) {
            if (uri.startsWith(prefix)) return true;
        }


        // storeId がない = 未ログイン
        boolean hasStoreId = false;
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("storeId".equals(cookie.getName())) {
                    hasStoreId = true;
                    break;
                }
            }
        }

        if (!hasStoreId) {
            response.sendRedirect("/login");
            return false;
        }

        return true;
    }
}
