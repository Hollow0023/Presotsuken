package com.order.util;

import org.springframework.stereotype.Component;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Cookie操作に関するユーティリティクラス
 * HTTPリクエストからCookie値を取得する共通処理を提供します
 */
@Component
public class CookieUtil {

    /**
     * HTTPリクエストからstoreIdのCookie値を取得します
     * 
     * @param request HTTPリクエスト
     * @return storeIdの値、Cookieが存在しないか無効な場合はnull
     */
    public Integer getStoreIdFromCookie(HttpServletRequest request) {
        return getCookieValueAsInteger(request, "storeId");
    }

    /**
     * HTTPリクエストから指定した名前のCookie値を整数として取得します
     * 
     * @param request HTTPリクエスト
     * @param cookieName Cookie名
     * @return Cookie値を整数に変換したもの、存在しないか変換できない場合はnull
     */
    public Integer getCookieValueAsInteger(HttpServletRequest request, String cookieName) {
        if (request.getCookies() == null) return null;
        
        for (Cookie cookie : request.getCookies()) {
            if (cookieName.equals(cookie.getName())) {
                String value = cookie.getValue();
                if (value == null || value.isEmpty() || value.equalsIgnoreCase("null") || value.equalsIgnoreCase("undefined")) {
                    return null;
                }
                try {
                    return Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    System.err.println("Warning: 無効なCookie値がIntegerに変換できませんでした (" + cookieName + "): " + value);
                    return null;
                }
            }
        }
        return null;
    }
}