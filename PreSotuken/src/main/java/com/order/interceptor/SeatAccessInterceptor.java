package com.order.interceptor;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 座席管理画面へのアクセスを管理者端末のみに制限するインターセプター
 */
@Component
public class SeatAccessInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String uri = request.getRequestURI();

        // /seats へのアクセスを管理者端末のみに制限
        if (uri.startsWith("/seats")) {
            boolean isAdmin = false;
            if (request.getCookies() != null) {
                for (Cookie cookie : request.getCookies()) {
                    if ("adminFlag".equals(cookie.getName()) && "true".equals(cookie.getValue())) {
                        isAdmin = true;
                        break;
                    }
                }
            }

            if (!isAdmin) {
                // 管理者でない場合は注文待機画面へリダイレクト
                response.sendRedirect("/visits/orderwait");
                return false;
            }
        }

        return true;
    }
}
