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
        int totalCustomers = 0;
        for (int i = 0; i < 24; i++) {
            cumulativeWithTax += hourlySalesWithTax[i];
            cumulativeWithoutTax += hourlySalesWithoutTax[i];
            totalCustomers += hourlyCustomers[i];
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

        // 合計行のデータを追加
        Map<String, Object> totalRow = new HashMap<>();
        totalRow.put("hour", -1); // 合計行を識別するための特殊値
        totalRow.put("customers", totalCustomers);
        totalRow.put("customerUnitPriceWithTax", totalCustomers > 0 ? cumulativeWithTax / totalCustomers : 0);
        totalRow.put("customerUnitPriceWithoutTax", totalCustomers > 0 ? cumulativeWithoutTax / totalCustomers : 0);
        totalRow.put("hourSalesWithTax", cumulativeWithTax);
        totalRow.put("hourSalesWithoutTax", cumulativeWithoutTax);
        totalRow.put("cumulativeSalesWithTax", cumulativeWithTax);
        totalRow.put("cumulativeSalesWithoutTax", cumulativeWithoutTax);

        model.addAttribute("date", date);
        model.addAttribute("hourlyData", hourlyData);
        model.addAttribute("totalRow", totalRow);
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

    @GetMapping("/sales-analysis/time-range")
    public String showTimeRangeSales(
            @CookieValue(name = "storeId", required = false) Integer storeId,
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "startTime", required = false) String startTime,
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(name = "endTime", required = false) String endTime,
            Model model) {
        
        // デフォルト値の設定: 本日の0:00から23:59まで
        if (startDate == null) {
            startDate = LocalDate.now();
        }
        if (startTime == null || startTime.isEmpty()) {
            startTime = "00:00";
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }
        if (endTime == null || endTime.isEmpty()) {
            endTime = "23:59";
        }

        // LocalDateTimeに変換
        LocalDateTime startDateTime = LocalDateTime.of(startDate, java.time.LocalTime.parse(startTime));
        LocalDateTime endDateTime = LocalDateTime.of(endDate, java.time.LocalTime.parse(endTime));

        // 終了時刻を次の分の開始時刻に調整（23:59 -> 24:00相当）
        endDateTime = endDateTime.plusMinutes(1);

        List<Payment> payments = paymentRepository
                .findByStoreStoreIdAndPaymentTimeBetween(storeId, startDateTime, endDateTime);

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
        int totalCustomers = 0;
        for (int i = 0; i < 24; i++) {
            cumulativeWithTax += hourlySalesWithTax[i];
            cumulativeWithoutTax += hourlySalesWithoutTax[i];
            totalCustomers += hourlyCustomers[i];
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

        // 合計行のデータを追加
        Map<String, Object> totalRow = new HashMap<>();
        totalRow.put("hour", -1);
        totalRow.put("customers", totalCustomers);
        totalRow.put("customerUnitPriceWithTax", totalCustomers > 0 ? cumulativeWithTax / totalCustomers : 0);
        totalRow.put("customerUnitPriceWithoutTax", totalCustomers > 0 ? cumulativeWithoutTax / totalCustomers : 0);
        totalRow.put("hourSalesWithTax", cumulativeWithTax);
        totalRow.put("hourSalesWithoutTax", cumulativeWithoutTax);
        totalRow.put("cumulativeSalesWithTax", cumulativeWithTax);
        totalRow.put("cumulativeSalesWithoutTax", cumulativeWithoutTax);

        model.addAttribute("startDate", startDate);
        model.addAttribute("startTime", startTime);
        model.addAttribute("endDate", endDate);
        model.addAttribute("endTime", endTime);
        model.addAttribute("hourlyData", hourlyData);
        model.addAttribute("totalRow", totalRow);
        return "sales-analysis-time-range";
    }

    @GetMapping("/sales-analysis/multi-day")
    public String showMultiDaySales(
            @CookieValue(name = "storeId", required = false) Integer storeId,
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Model model) {
        
        // デフォルト値の設定: 過去7日間
        if (endDate == null) {
            endDate = LocalDate.now();
        }
        if (startDate == null) {
            startDate = endDate.minusDays(6);
        }

        // 日付ごと、時間ごとの売上データを格納
        List<Map<String, Object>> dailyHourlyData = new ArrayList<>();
        
        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            LocalDateTime startOfDay = currentDate.atStartOfDay();
            LocalDateTime endOfDay = startOfDay.plusDays(1);
            
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
            
            Map<String, Object> dayData = new HashMap<>();
            dayData.put("date", currentDate);
            dayData.put("hourlySalesWithTax", hourlySalesWithTax);
            dayData.put("hourlySalesWithoutTax", hourlySalesWithoutTax);
            dayData.put("hourlyCustomers", hourlyCustomers);
            
            // 日次合計を計算
            double dailyTotalWithTax = 0;
            double dailyTotalWithoutTax = 0;
            int dailyTotalCustomers = 0;
            for (int i = 0; i < 24; i++) {
                dailyTotalWithTax += hourlySalesWithTax[i];
                dailyTotalWithoutTax += hourlySalesWithoutTax[i];
                dailyTotalCustomers += hourlyCustomers[i];
            }
            dayData.put("dailyTotalWithTax", dailyTotalWithTax);
            dayData.put("dailyTotalWithoutTax", dailyTotalWithoutTax);
            dayData.put("dailyTotalCustomers", dailyTotalCustomers);
            
            dailyHourlyData.add(dayData);
            currentDate = currentDate.plusDays(1);
        }
        
        // 時間ごとの合計（全日合計）を計算
        double[] hourlyTotalWithTax = new double[24];
        double[] hourlyTotalWithoutTax = new double[24];
        int[] hourlyTotalCustomers = new int[24];
        double grandTotalWithTax = 0;
        double grandTotalWithoutTax = 0;
        int grandTotalCustomers = 0;
        
        for (Map<String, Object> dayData : dailyHourlyData) {
            double[] salesWithTax = (double[]) dayData.get("hourlySalesWithTax");
            double[] salesWithoutTax = (double[]) dayData.get("hourlySalesWithoutTax");
            int[] customers = (int[]) dayData.get("hourlyCustomers");
            for (int i = 0; i < 24; i++) {
                hourlyTotalWithTax[i] += salesWithTax[i];
                hourlyTotalWithoutTax[i] += salesWithoutTax[i];
                hourlyTotalCustomers[i] += customers[i];
            }
            grandTotalWithTax += (Double) dayData.get("dailyTotalWithTax");
            grandTotalWithoutTax += (Double) dayData.get("dailyTotalWithoutTax");
            grandTotalCustomers += (Integer) dayData.get("dailyTotalCustomers");
        }
        
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("dailyHourlyData", dailyHourlyData);
        model.addAttribute("hourlyTotalWithTax", hourlyTotalWithTax);
        model.addAttribute("hourlyTotalWithoutTax", hourlyTotalWithoutTax);
        model.addAttribute("hourlyTotalCustomers", hourlyTotalCustomers);
        model.addAttribute("grandTotalWithTax", grandTotalWithTax);
        model.addAttribute("grandTotalWithoutTax", grandTotalWithoutTax);
        model.addAttribute("grandTotalCustomers", grandTotalCustomers);
        
        return "sales-analysis-multi-day";
    }
}

