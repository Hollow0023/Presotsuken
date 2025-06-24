package com.order.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime; // 追加
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.order.dto.CashTransactionRequest;
import com.order.entity.CashTransaction;
import com.order.entity.Store;
import com.order.repository.StoreRepository;
import com.order.service.CashTransactionService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/admin/cash")
@RequiredArgsConstructor
public class CashTransactionController {

    private final CashTransactionService transactionService;
    private final StoreRepository storeRepository;

    @PostMapping("/transaction")
    public String registerTransaction(@ModelAttribute @Valid CashTransactionRequest request,
                                      HttpServletRequest httpRequest) {
        transactionService.saveTransaction(request, httpRequest);
        return "redirect:/admin/cash/transaction?success=true";
    }

    @GetMapping("/transaction")
    public String showTransactionForm(HttpServletRequest request, Model model) {
        model.addAttribute("cashTransactionRequest", new CashTransactionRequest());
        model.addAttribute("users", transactionService.getUsersForStore(request));
        return "admin/cashTransactionForm";
    }

    @GetMapping("/history")
    public String showCashTransactionHistory(
            @RequestParam(value = "date", required = false) LocalDate date,
            @RequestParam(value = "type", required = false) String type,
            HttpServletRequest httpRequest,
            Model model
    ) {
        // デフォルトで今日の日付を設定する
        if (date == null) {
            date = LocalDate.now();
        }

        // --- Store IDの取得ロジック (変更なし) ---
        Integer storeId = null;
        Cookie[] cookies = httpRequest.getCookies();
        if (cookies != null) {
            Optional<Cookie> storeIdCookie = Arrays.stream(cookies)
                .filter(cookie -> "storeId".equals(cookie.getName()))
                .findFirst();

            if (storeIdCookie.isPresent()) {
                try {
                    storeId = Integer.parseInt(storeIdCookie.get().getValue());
                } catch (NumberFormatException e) {
                    System.err.println("Error parsing storeId from cookie: " + e.getMessage());
                }
            }
        }

        if (storeId == null) {
            model.addAttribute("errorMessage", "店舗情報が取得できませんでした。再度ログインしてください。");
            return "errorPage";
        }
        // --- ここまでStore IDの取得ロジック ---

        // ★ここから日付の開始と終了時刻の算出ロジックを再修正するよ★
        LocalTime transitionTime;
        Optional<Store> storeOptional = storeRepository.findById(storeId);
        if (storeOptional.isPresent()) {
            transitionTime = storeOptional.get().getTransitionTime();
        } else {
            System.err.println("Store not found for ID: " + storeId + ". Using default transition time (00:00).");
            transitionTime = LocalTime.MIDNIGHT;
        }

        LocalDateTime startOfPeriod;
        LocalDateTime endOfPeriod;

        // 検索対象の基準となる日を設定
        // 例えば、2025/06/24 を選択した場合
        // transitionTimeがAM3:00なら
        // 今日が2025/06/24 10:00 ならば、2025/06/24 3:00 ～ 2025/06/25 3:00-1nano を検索したい
        // 今日が2025/06/24 01:00 ならば、2025/06/23 3:00 ～ 2025/06/24 3:00-1nano を検索したい
        // というロジックだったね！
        // ユーザーが選択した日付 'date' を基準に期間を決定する

        // 選択された 'date' を基準に、その日の transitionTime を含む期間を決定する
        // 例: dateが 2025-06-24, transitionTimeが 03:00 の場合
        // startOfPeriod = 2025-06-24 03:00:00
        // endOfPeriod   = 2025-06-25 02:59:59.999999999
        startOfPeriod = date.atTime(transitionTime);
        endOfPeriod = date.plusDays(1).atTime(transitionTime).minusNanos(1);

        // ★ここまで日付の開始と終了時刻の算出ロジックを再修正したよ★

        // サービス層からデータを取得 (storeIdと新しい開始・終了時刻を引数に)
        List<CashTransaction> transactions;
        if (type != null && !type.isEmpty()) {
            transactions = transactionService.getCashTransactionsByDateAndType(
                storeId, startOfPeriod, endOfPeriod, type
            );
        } else {
            transactions = transactionService.getCashTransactionsByDate(
                storeId, startOfPeriod, endOfPeriod
            );
        }

        model.addAttribute("transactions", transactions);
        model.addAttribute("selectedDate", date);
        model.addAttribute("selectedType", type);

        return "admin/cashTransactionHistory";
    }
}