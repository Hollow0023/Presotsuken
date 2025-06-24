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
import com.order.entity.CashTransaction;
import com.order.entity.InspectionLog;
import com.order.entity.PaymentType;
import com.order.entity.User;
import com.order.repository.CashTransactionRepository;
import com.order.repository.InspectionLogRepository;
import com.order.repository.PaymentDetailRepository;
import com.order.repository.PaymentRepository;
import com.order.repository.PaymentTypeRepository;
import com.order.repository.StoreRepository;
import com.order.repository.UserRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InspectionLogService {

    private final PaymentDetailRepository paymentDetailRepository;
    private final PaymentRepository paymentRepository;
    private final InspectionLogRepository inspectionLogRepository;
    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final CashTransactionRepository cashTransactionRepository;
    private final PaymentTypeRepository paymentTypeRepository;

    private static final int[] DENOMINATIONS = {10000, 5000, 1000, 500, 100, 50, 10, 5, 1};

    @Transactional
    public void registerInspection(Integer storeId, InspectionLogRequest request, boolean performWithdrawal) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.with(LocalTime.of(3, 0));
        if (now.isBefore(start)) start = start.minusDays(1);
        LocalDateTime end = start.plusDays(1);

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
        double actualDouble = (double) actual;

        // 想定金額を再計算（売上 + 入金 - 出金）
        BigDecimal deposit = cashTransactionRepository.sumAmountByType(storeId, "IN", start, end);
        BigDecimal withdraw = cashTransactionRepository.sumAmountByType(storeId, "OUT", start, end);
        BigDecimal expectedCash = totalCashSales.add(safeBig(deposit)).subtract(safeBig(withdraw));
        
        if (performWithdrawal) {
            // 出金を行うチェックボックスがオンの場合
            // 現金合計 (actualAmount) を出金として登録する
            CashTransaction withdrawalTransaction = new CashTransaction();
            withdrawalTransaction.setStore(storeRepository.findById(storeId).orElseThrow());
            withdrawalTransaction.setAmount(actualDouble); // 現金合計を金額として設定
            withdrawalTransaction.setType("OUT"); // 出金タイプ
            withdrawalTransaction.setReason("点検時の出金（レジ現金調整）"); // 理由
            withdrawalTransaction.setTransactionTime(now); // 現在時刻
            
             withdrawalTransaction.setUser(userRepository.findById(request.getUserId()).orElseThrow()); // 必要なら登録者も設定
            
            cashTransactionRepository.save(withdrawalTransaction);
        }

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

        Map<String, Object> result = new HashMap<>();

        // 安全に合計値を扱うためのヘルパー
        Function<BigDecimal, BigDecimal> safe = val -> val != null ? val : BigDecimal.ZERO;
        
        boolean inspectionCompletedToday = inspectionLogRepository.existsByStoreIdAndInspectionTimeBetween(storeId, start, end);
        result.put("inspectionCompletedToday", inspectionCompletedToday);

        // 現金売上（点検対象の支払種別）
        BigDecimal cashSales = safe.apply(paymentRepository.sumCashSales(storeId, start, end));
        result.put("cashSalesPure", cashSales);
//        result.put("expectedCashSales", cashSales);

        // 総売上
        BigDecimal totalSales = safe.apply(paymentDetailRepository.sumTotalSales(storeId, start, end));
        result.put("total", totalSales);

        // 税率別売上 (既存のコード、これは画面上部の総合計用として残しておく)
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

        // ★★★ ここから新しい会計種別ごとの税率別売上を追加 ★★★
        Map<String, BigDecimal> salesByPaymentTypeAndTax = new HashMap<>();
        List<Object[]> salesData = paymentDetailRepository.sumSalesByPaymentTypeAndTaxRate(storeId, start, end);
        
        for (Object[] row : salesData) {
            String typeName = (String) row[0]; // 例: "現金", "カード"
            Integer taxRateId = (Integer) row[1]; // 例: 1 (10%), 2 (8%)
            BigDecimal sum = safe.apply(BigDecimal.valueOf((Double) row[2])); // 合計金額

            String key = "salesByPaymentType_" + typeName;
            if (taxRateId == 1) { // 10%税率の場合
                key += "_10%";
            } else if (taxRateId == 2) { // 8%税率の場合
                key += "_8%";
            } else {
                // その他の税率IDの場合の処理（必要であれば）
                key += "_その他"; // もし他の税率があるなら対応を検討
            }
            salesByPaymentTypeAndTax.put(key, sum);
        }
        result.putAll(salesByPaymentTypeAndTax); // Mapごとresultに追加

        // ★★★ ここまで新しい会計種別ごとの税率別売上を追加 ★★★

        // 消費税額 (既存のコード)
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
        
        List<PaymentType> allPaymentTypes = paymentTypeRepository.findAllByOrderByTypeNameAsc(); // ソートして取得すると表示がきれいだよ
        result.put("allPaymentTypes", allPaymentTypes);

        return result;
    }

    
    
}
