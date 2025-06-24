package com.order.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.order.interceptor.AdminPageInterceptor;
import com.order.interceptor.LoginCheckInterceptor;

@Configuration
public class WebConfig implements WebMvcConfigurer {
	 private final LoginCheckInterceptor loginCheckInterceptor;
     private final AdminPageInterceptor adminPageInterceptor;

    @Value("${upload.path}")
    private String uploadPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // /images/** でアクセスされたら、uploadPath から読み込む
        registry.addResourceHandler("/images/**")
                .addResourceLocations("file:" + uploadPath + "/");
    }
    
    public WebConfig(LoginCheckInterceptor loginCheckInterceptor, AdminPageInterceptor adminPageInterceptor) {
        this.loginCheckInterceptor = loginCheckInterceptor;
        this.adminPageInterceptor = adminPageInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginCheckInterceptor).addPathPatterns("/**");
        registry.addInterceptor(adminPageInterceptor).addPathPatterns("/admin/**");
    }
}


