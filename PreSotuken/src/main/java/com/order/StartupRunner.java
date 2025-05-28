package com.order;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import com.order.repository.StoreRepository;

@Component
public class StartupRunner {

    @Bean
    public CommandLineRunner run(StoreRepository storeRepository) {
        return args -> {
            System.out.println("=== Store Table Test ===");
            storeRepository.findAll().forEach(store ->
                System.out.println("Store ID: " + store.getStoreId() + ", Name: " + store.getStoreName())
            );
        };
    }
}
