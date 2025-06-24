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
    
    
    /**
     * 指定された店舗IDと期間で入出金履歴を取得します。（タイプによる絞り込みなし）
     * @param storeId 店舗ID
     * @param startOfPeriod 検索期間の開始日時
     * @param endOfPeriod 検索期間の終了日時
     * @return 入出金履歴のリスト
     */
    public List<CashTransaction> getCashTransactionsByDate(
            Integer storeId, LocalDateTime startOfPeriod, LocalDateTime endOfPeriod) {
        return transactionRepo.findByStore_StoreIdAndTransactionTimeBetween(
                storeId, startOfPeriod, endOfPeriod);
    }

    /**
     * 指定された店舗ID、期間、タイプで入出金履歴を取得します。（タイプによる絞り込みあり）
     * @param storeId 店舗ID
     * @param startOfPeriod 検索期間の開始日時
     * @param endOfPeriod 検索期間の終了日時
     * @param type 入出金タイプ ("IN" or "OUT")
     * @return 入出金履歴のリスト
     */
    public List<CashTransaction> getCashTransactionsByDateAndType(
            Integer storeId, LocalDateTime startOfPeriod, LocalDateTime endOfPeriod, String type) {
        return transactionRepo.findByStore_StoreIdAndTypeAndTransactionTimeBetween(
                storeId, type, startOfPeriod, endOfPeriod);
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
