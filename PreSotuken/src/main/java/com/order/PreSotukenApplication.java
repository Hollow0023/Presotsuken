package com.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class PreSotukenApplication {

	public static void main(String[] args) {
		SpringApplication.run(PreSotukenApplication.class, args);
	}
	
	@Bean // SpringのコンテキストにBeanとして登録することを指示
    public RestTemplate restTemplate() {
        return new RestTemplate();
}


}
