package com.order.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.order.entity.Payment;
import com.order.repository.PaymentDetailRepository;
import com.order.repository.PaymentRepository;
import com.order.repository.StoreRepository;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Controller
public class SalesAnalysisController {

    private final PaymentRepository paymentRepository;
    private final PaymentDetailRepository paymentDetailRepository;
    private final StoreRepository storeRepository;

    @GetMapping("/sales-analysis")
    public String showDailySales(
            @CookieValue(name = "storeId", required = false) Integer storeId,
            @RequestParam(name = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Model model) {
        if (date == null) {
            date = LocalDate.now();
        }
        
        // 店舗の区切り時間を取得（デフォルトは3:00）
        LocalTime transitionTime = storeRepository.findById(storeId)
            .map(store -> store.getTransitionTime() != null ? store.getTransitionTime() : LocalTime.of(3, 0))
            .orElse(LocalTime.of(3, 0));
        
        // 選択された日付の区切り時間から次の日の区切り時間までの期間を取得
        LocalDateTime startOfDay = date.atTime(transitionTime);
        LocalDateTime endOfDay = date.plusDays(1).atTime(transitionTime);
        
        List<Payment> payments = paymentRepository
                .findByStoreStoreIdAndPaymentTimeBetween(storeId, startOfDay, endOfDay);

        double[] hourlySalesWithTax = new double[24];
        double[] hourlySalesWithoutTax = new double[24];
        int[] hourlyCustomers = new int[24];
        for (Payment p : payments) {
            if (p.getPaymentTime() == null) continue;
            int hour = p.getPaymentTime().getHour();
            double total = p.getTotal() != null ? p.getTotal() : 0;
            hourlySalesWithTax[hour] += total;
            double subtotal = paymentDetailRepository.findByPaymentPaymentId(p.getPaymentId())
                    .stream()
                    .mapToDouble(pd -> pd.getSubtotal() != null ? pd.getSubtotal() : 0)
                    .sum();
            hourlySalesWithoutTax[hour] += subtotal;
            if (p.getVisit() != null && p.getVisit().getNumberOfPeople() != null) {
                hourlyCustomers[hour] += p.getVisit().getNumberOfPeople();
            }
        }

        List<Map<String, Object>> hourlyData = new ArrayList<>();
        double cumulativeWithTax = 0;
        double cumulativeWithoutTax = 0;
        for (int i = 0; i < 24; i++) {
            cumulativeWithTax += hourlySalesWithTax[i];
            cumulativeWithoutTax += hourlySalesWithoutTax[i];
            Map<String, Object> m = new HashMap<>();
            m.put("hour", i);
            m.put("customers", hourlyCustomers[i]);
            m.put("customerUnitPriceWithTax", hourlyCustomers[i] > 0 ? hourlySalesWithTax[i] / hourlyCustomers[i] : 0);
            m.put("customerUnitPriceWithoutTax", hourlyCustomers[i] > 0 ? hourlySalesWithoutTax[i] / hourlyCustomers[i] : 0);
            m.put("hourSalesWithTax", hourlySalesWithTax[i]);
            m.put("hourSalesWithoutTax", hourlySalesWithoutTax[i]);
            m.put("cumulativeSalesWithTax", cumulativeWithTax);
            m.put("cumulativeSalesWithoutTax", cumulativeWithoutTax);
            hourlyData.add(m);
        }

        model.addAttribute("date", date);
        model.addAttribute("hourlyData", hourlyData);
        return "sales-analysis";
    }

    @GetMapping("/sales-analysis/details")
    public ResponseEntity<List<Map<String, Object>>> getHourlyDetails(
            @CookieValue(name = "storeId", required = false) Integer storeId,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam("hour") int hour) {
        // 店舗の区切り時間を取得（デフォルトは3:00）
        LocalTime transitionTime = storeRepository.findById(storeId)
            .map(store -> store.getTransitionTime() != null ? store.getTransitionTime() : LocalTime.of(3, 0))
            .orElse(LocalTime.of(3, 0));
        
        // 選択された日付の区切り時間を基準として、指定された時間の範囲を計算
        LocalDateTime start = date.atTime(transitionTime).plusHours(hour);
        LocalDateTime end = start.plusHours(1);
        
        List<Object[]> result = paymentDetailRepository.sumMenuQuantityByTime(storeId, start, end);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Object[] r : result) {
            Map<String, Object> m = new HashMap<>();
            m.put("menuName", r[0]);
            m.put("quantity", ((Number) r[1]).intValue());
            m.put("priceWithoutTax", ((Number) r[2]).doubleValue());
            m.put("priceWithTax", ((Number) r[3]).doubleValue());

            list.add(m);
        }
        return ResponseEntity.ok(list);
    }
}

