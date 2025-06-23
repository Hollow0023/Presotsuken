package com.order.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.order.dto.CashTransactionRequest;
import com.order.entity.CashTransaction;
import com.order.entity.Store;
import com.order.entity.User;
import com.order.repository.CashTransactionRepository;
import com.order.repository.StoreRepository;
import com.order.repository.UserRepository;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CashTransactionService {

    private final CashTransactionRepository transactionRepo;
    private final StoreRepository storeRepo;
    private final UserRepository userRepo;

    public void saveTransaction(CashTransactionRequest request, HttpServletRequest httpReq) {

        // storeId はクッキーから取得
        Integer storeId = getCookieValue(httpReq, "storeId");
        Store store = storeRepo.findById(storeId).orElseThrow();

        User user = userRepo.findById(request.getUserId()).orElseThrow();

        CashTransaction transaction = CashTransaction.builder()
                .store(store)
                .user(user)
                .type(request.getType())
                .amount(request.getAmount())
                .reason(request.getReason())
                .transactionTime(LocalDateTime.now())
                .build();

        transactionRepo.save(transaction);
    }

    private Integer getCookieValue(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (cookie.getName().equals(name)) {
                return Integer.parseInt(cookie.getValue());
            }
        }
        return null;
    }
    
    public List<User> getUsersForStore(HttpServletRequest request) {
        Integer storeId = getStoreIdFromCookies(request);
        return userRepo.findByStore_StoreId(storeId);
    }

    private Integer getStoreIdFromCookies(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("storeId".equals(cookie.getName())) {
                    try {
                        return Integer.parseInt(cookie.getValue());
                    } catch (NumberFormatException e) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

}
