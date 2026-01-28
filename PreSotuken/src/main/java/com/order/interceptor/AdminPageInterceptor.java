package com.order.interceptor;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class AdminPageInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String uri = request.getRequestURI();

        if (uri.startsWith("/admin")) {
            boolean isAdmin = false;
            boolean isPendingTerminalRegistration = false;
            
            if (request.getCookies() != null) {
                for (Cookie cookie : request.getCookies()) {
                    if ("adminFlag".equals(cookie.getName()) && "true".equals(cookie.getValue())) {
                        isAdmin = true;
                    }
                    if ("pendingTerminalRegistration".equals(cookie.getName()) && "true".equals(cookie.getValue())) {
                        isPendingTerminalRegistration = true;
                    }
                }
            }

            // 端末登録待ちの場合は /admin/terminals のみアクセス可能
            if (isPendingTerminalRegistration) {
                if (!uri.startsWith("/admin/terminals")) {
                    response.sendRedirect("/admin/terminals");
                    return false;
                }
                return true;
            }

            // 通常の管理者チェック
            if (!isAdmin) {
                response.sendRedirect("/login?admin=denied");
                return false;
            }
        }

        return true;
    }
}
