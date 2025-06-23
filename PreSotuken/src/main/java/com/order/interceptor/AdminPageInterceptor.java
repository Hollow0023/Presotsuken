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
            if (request.getCookies() != null) {
                for (Cookie cookie : request.getCookies()) {
                    if ("adminFlag".equals(cookie.getName()) && "true".equals(cookie.getValue())) {
                        isAdmin = true;
                        break;
                    }
                }
            }

            if (!isAdmin) {
                response.sendRedirect("/login?admin=denied");
                return false;
            }
        }

        return true;
    }
}
