package com.order.service;

import org.springframework.stereotype.Component;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

@Component
public class CookieUtil {

    /**
     * リクエストに含まれる Cookie から storeId を取得する。
     * 数値変換に失敗した場合は null を返す。
     *
     * @param request 現在の HTTP リクエスト
     * @return Cookie に保存された storeId、存在しない場合は null
     */
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
