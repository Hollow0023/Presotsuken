package com.order.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.order.entity.Store;
import com.order.entity.User;
import com.order.repository.StoreRepository;
import com.order.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final StoreRepository storeRepository;

    /**
     * 店舗IDに基づいてスタッフ一覧を取得
     */
    public List<User> getUsersByStoreId(Integer storeId) {
        return userRepository.findByStore_StoreId(storeId);
    }

    /**
     * スタッフを追加
     */
    public User createUser(String userName, Boolean isAdmin, Integer storeId) {
        if (userName == null || userName.trim().isEmpty()) {
            throw new IllegalArgumentException("ユーザー名は必須です。");
        }
        
        Store store = storeRepository.findById(storeId)
            .orElseThrow(() -> new IllegalArgumentException("指定された店舗が見つかりません。"));
        
        User user = new User();
        user.setUserName(userName.trim());
        user.setIsAdmin(isAdmin != null ? isAdmin : false);
        user.setStore(store);
        
        return userRepository.save(user);
    }

    /**
     * スタッフを更新
     */
    public User updateUser(Integer userId, String userName, Boolean isAdmin, Integer storeId) {
        User existingUser = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("指定されたユーザーが見つかりません。"));
        
        // 店舗の権限チェック
        if (!existingUser.getStore().getStoreId().equals(storeId)) {
            throw new IllegalArgumentException("このユーザーを編集する権限がありません。");
        }
        
        if (userName == null || userName.trim().isEmpty()) {
            throw new IllegalArgumentException("ユーザー名は必須です。");
        }
        
        existingUser.setUserName(userName.trim());
        existingUser.setIsAdmin(isAdmin != null ? isAdmin : false);
        
        return userRepository.save(existingUser);
    }

    /**
     * スタッフを削除
     */
    public void deleteUser(Integer userId, Integer storeId) {
        User existingUser = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("指定されたユーザーが見つかりません。"));
        
        // 店舗の権限チェック
        if (!existingUser.getStore().getStoreId().equals(storeId)) {
            throw new IllegalArgumentException("このユーザーを削除する権限がありません。");
        }
        
        userRepository.delete(existingUser);
    }

    /**
     * ユーザーIDでユーザーを取得
     */
    public Optional<User> getUserById(Integer userId) {
        return userRepository.findById(userId);
    }
}