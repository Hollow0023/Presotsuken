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

/**
 * アプリケーション起動時に実行される初期化処理を定義するクラス
 * テスト用のデータを作成し、システムの基本的な動作環境を準備します
 */
@Component
public class StartupRunner {

    /**
     * アプリケーション起動時に実行される初期化処理を定義します
     * テスト用の店舗、端末、ユーザーを作成します
     * 
     * @param storeRepository 店舗リポジトリ
     * @param terminalRepository 端末リポジトリ  
     * @param userRepository ユーザーリポジトリ
     * @return CommandLineRunnerの実装
     */
    @Bean
    public CommandLineRunner run(StoreRepository storeRepository, 
                                TerminalRepository terminalRepository,
                                UserRepository userRepository) {
        return args -> {
            System.out.println("=== Initializing Test Data ===");
            
            // テスト店舗が存在しない場合は作成
            if (storeRepository.count() == 0) {
                Store testStore = new Store();
                testStore.setStoreName("テスト店舗");
                testStore = storeRepository.save(testStore);
                System.out.println("Created test store: " + testStore.getStoreName());
                
                // テスト用管理者端末を作成 (IP: 127.0.0.1)
                Terminal adminTerminal = new Terminal();
                adminTerminal.setStore(testStore);
                adminTerminal.setIpAddress("127.0.0.1");
                adminTerminal.setAdmin(true);
                terminalRepository.save(adminTerminal);
                System.out.println("Created admin terminal for IP: 127.0.0.1");
                
                // テストユーザーを作成
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
            
            // 登録済み店舗の一覧を表示
            storeRepository.findAll().forEach(store ->
                System.out.println("Store ID: " + store.getStoreId() + ", Name: " + store.getStoreName())
            );
        };
    }
}
