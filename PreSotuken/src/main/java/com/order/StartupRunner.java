package com.order;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import com.order.entity.Store;
import com.order.entity.Terminal;
import com.order.entity.User;
import com.order.repository.StoreRepository;
import com.order.repository.TerminalRepository;
import com.order.repository.UserRepository;

@Component
public class StartupRunner {

    @Bean
    public CommandLineRunner run(StoreRepository storeRepository, 
                                TerminalRepository terminalRepository,
                                UserRepository userRepository) {
        return args -> {
            System.out.println("=== Initializing Test Data ===");
            
            // Create test store if it doesn't exist
            if (storeRepository.count() == 0) {
                Store testStore = new Store();
                testStore.setStoreName("テスト店舗");
                testStore = storeRepository.save(testStore);
                System.out.println("Created test store: " + testStore.getStoreName());
                
                // Create admin terminal for testing (IP: 127.0.0.1)
                Terminal adminTerminal = new Terminal();
                adminTerminal.setStore(testStore);
                adminTerminal.setIpAddress("127.0.0.1");
                adminTerminal.setAdmin(true);
                terminalRepository.save(adminTerminal);
                System.out.println("Created admin terminal for IP: 127.0.0.1");
                
                // Create test users
                User adminUser = new User();
                adminUser.setUserName("管理者ユーザー");
                adminUser.setIsAdmin(true);
                adminUser.setStore(testStore);
                userRepository.save(adminUser);
                
                User normalUser = new User();
                normalUser.setUserName("一般スタッフ");
                normalUser.setIsAdmin(false);
                normalUser.setStore(testStore);
                userRepository.save(normalUser);
                
                System.out.println("Created test users");
            }
            
            storeRepository.findAll().forEach(store ->
                System.out.println("Store ID: " + store.getStoreId() + ", Name: " + store.getStoreName())
            );
        };
    }
}
