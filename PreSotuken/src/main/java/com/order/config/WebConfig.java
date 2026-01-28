package com.order.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.order.interceptor.AdminPageInterceptor;
import com.order.interceptor.LoginCheckInterceptor;
import com.order.interceptor.SeatAccessInterceptor;

/**
 * Web MVC設定を行うコンフィギュレーションクラス
 * リソースハンドラーとインターセプターの設定を担当します
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final LoginCheckInterceptor loginCheckInterceptor;
    private final AdminPageInterceptor adminPageInterceptor;
    private final SeatAccessInterceptor seatAccessInterceptor;

    @Value("${upload.path}")
    private String uploadPath;

    /**
     * コンストラクタ
     * 
     * @param loginCheckInterceptor ログインチェックインターセプター
     * @param adminPageInterceptor 管理者ページアクセスチェックインターセプター
     * @param seatAccessInterceptor 座席画面アクセスチェックインターセプター
     */
    public WebConfig(LoginCheckInterceptor loginCheckInterceptor, AdminPageInterceptor adminPageInterceptor, SeatAccessInterceptor seatAccessInterceptor) {
        this.loginCheckInterceptor = loginCheckInterceptor;
        this.adminPageInterceptor = adminPageInterceptor;
        this.seatAccessInterceptor = seatAccessInterceptor;
    }

    /**
     * 静的リソースハンドラーを設定します
     * /images/** へのアクセスを uploadPath から読み込むように設定します
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/images/**")
                .addResourceLocations("file:" + uploadPath + "/");
    }
    
    /**
     * インターセプターを登録します
     * ログインチェック、管理者ページアクセスチェック、座席画面アクセスチェックを設定します
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginCheckInterceptor).addPathPatterns("/**");
        registry.addInterceptor(adminPageInterceptor).addPathPatterns("/admin/**");
        registry.addInterceptor(seatAccessInterceptor).addPathPatterns("/seats/**");
    }
}
