package com.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

/**
 * PreSotuken（注文管理システム）のメインアプリケーションクラス
 * Spring Bootアプリケーションの起動ポイントとして機能します
 */
@SpringBootApplication
public class PreSotukenApplication {

	/**
	 * アプリケーションのメインメソッド
	 * 
	 * @param args コマンドライン引数
	 */
	public static void main(String[] args) {
		SpringApplication.run(PreSotukenApplication.class, args);
	}
	
	/**
	 * RestTemplateをSpringのBeanとして登録します
	 * HTTP通信を行う際に使用されます
	 * 
	 * @return RestTemplateのインスタンス
	 */
	@Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
