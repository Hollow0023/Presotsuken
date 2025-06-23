package com.order.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.stereotype.Service;

import com.order.dto.InspectionLogRequest;
import com.order.entity.InspectionLog;
import com.order.entity.User;
import com.order.repository.CashTransactionRepository;
import com.order.repository.InspectionLogRepository;
import com.order.repository.PaymentDetailRepository;
import com.order.repository.PaymentRepository;
import com.order.repository.StoreRepository;
import com.order.repository.UserRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class InspectionLogService {

    private final PaymentDetailRepository paymentDetailRepository;
    private final PaymentRepository paymentRepository;
    private final InspectionLogRepository inspectionLogRepository;
    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final CashTransactionRepository cashTransactionRepository;

    private static final int[] DENOMINATIONS = {10000, 5000, 1000, 500, 100, 50, 10, 5, 1};

    @Transactional
    public void registerInspection(Integer storeId, InspectionLogRequest request) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.with(LocalTime.of(3, 0));
        if (now.isBefore(start)) start = start.minusDays(1);
        LocalDateTime end = start.plusDays(1);
        
        log.info("🔍 buildInspectionSummary: storeId={}, start={}, end={}",
                storeId, start, end);

        BigDecimal totalCashSales = paymentRepository.sumCashSales(storeId, start, end);
        if (totalCashSales == null) totalCashSales = BigDecimal.ZERO;

        // 各金種の合計を手動で計算
        int actual = 0;
        actual += 10000 * safeInt(request.getYen10000());
        actual += 5000  * safeInt(request.getYen5000());
        actual += 1000  * safeInt(request.getYen1000());
        actual += 500   * safeInt(request.getYen500());
        actual += 100   * safeInt(request.getYen100());
        actual += 50    * safeInt(request.getYen50());
        actual += 10    * safeInt(request.getYen10());
        actual += 5     * safeInt(request.getYen5());
        actual += 1     * safeInt(request.getYen1());

        // 想定金額を再計算（売上 + 入金 - 出金）
        BigDecimal deposit = cashTransactionRepository.sumAmountByType(storeId, "IN", start, end);
        BigDecimal withdraw = cashTransactionRepository.sumAmountByType(storeId, "OUT", start, end);
        BigDecimal expectedCash = totalCashSales.add(safeBig(deposit)).subtract(safeBig(withdraw));

        InspectionLog log = new InspectionLog();
        log.setStore(storeRepository.findById(storeId).orElseThrow());
        log.setUser(userRepository.findById(request.getUserId()).orElseThrow());
        log.setInspectionTime(now);
        log.setTotalCashSales(totalCashSales);
        log.setActualCashAmount(BigDecimal.valueOf(actual));
        log.setDifferenceAmount(BigDecimal.valueOf(actual).subtract(expectedCash));

        inspectionLogRepository.save(log);
    }
    
    private int safeInt(Integer value) {
        return value != null ? value : 0;
    }

    private BigDecimal safeBig(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }



    public List<User> getUsersForStore(Integer storeId) {
        return userRepository.findByStore_StoreId(storeId);
    }

    public Map<String, Object> buildInspectionSummary(Integer storeId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        LocalDateTime start = LocalDateTime.of(today, LocalTime.of(3, 0));
        LocalDateTime end = start.plusDays(1);
        log.info("🔍 buildInspectionSummary: storeId={}, start={}, end={}",
                storeId, start, end);

        Map<String, Object> result = new HashMap<>();

        // 安全に合計値を扱うためのヘルパー
        Function<BigDecimal, BigDecimal> safe = val -> val != null ? val : BigDecimal.ZERO;

        // 現金売上（点検対象の支払種別）
        BigDecimal cashSales = safe.apply(paymentRepository.sumCashSales(storeId, start, end));
        result.put("cashSalesPure", cashSales);
        result.put("expectedCashSales", cashSales);

        // 総売上
        BigDecimal totalSales = safe.apply(paymentDetailRepository.sumTotalSales(storeId, start, end));
        result.put("total", totalSales);

        // 税率別売上
        BigDecimal tax10Sales = BigDecimal.ZERO;
        BigDecimal tax8Sales = BigDecimal.ZERO;
        for (Object[] row : paymentDetailRepository.sumSalesByTaxRate(storeId, start, end)) {
            Integer taxRateId = (Integer) row[0];
            BigDecimal sum = safe.apply(BigDecimal.valueOf((Double) row[1]));
            if (taxRateId == 1) tax10Sales = sum;
            if (taxRateId == 2) tax8Sales = sum;
        }
        result.put("tax10", tax10Sales);
        result.put("tax8", tax8Sales);

        // 消費税額
        BigDecimal taxAmount10 = safe.apply(paymentDetailRepository.sumTaxAmount(storeId, start, end, new BigDecimal("0.10")));
        BigDecimal taxAmount8 = safe.apply(paymentDetailRepository.sumTaxAmount(storeId, start, end, new BigDecimal("0.08")));
        result.put("taxAmount10", taxAmount10);
        result.put("taxAmount8", taxAmount8);

        // 割引
        BigDecimal discountCash = BigDecimal.ZERO;
        BigDecimal discountCard = BigDecimal.ZERO;
        for (Object[] row : paymentDetailRepository.sumDiscountByPaymentType(storeId, start, end)) {
            String typeName = (String) row[0];
            BigDecimal sum = safe.apply(BigDecimal.valueOf((Double) row[1]));
            if ("現金".equals(typeName)) discountCash = sum;
            if ("カード".equals(typeName)) discountCard = sum;
        }
        result.put("discountCash", discountCash);
        result.put("discountCard", discountCard);



        // 客数
        Long guestCount = paymentRepository.countCustomerVisits(storeId, start, end);
        result.put("guestCount", guestCount != null ? guestCount : 0L);

        // 入出金
        BigDecimal deposit = safe.apply(cashTransactionRepository.sumAmountByType(storeId, "IN", start, end));
        BigDecimal withdraw = safe.apply(cashTransactionRepository.sumAmountByType(storeId, "OUT", start, end));

        // 想定金額（現金売上 + 入金 − 出金）
        BigDecimal expectedCash = cashSales.add(deposit).subtract(withdraw);
        result.put("expectedCash", expectedCash);

        return result;
    }

    
    
}
