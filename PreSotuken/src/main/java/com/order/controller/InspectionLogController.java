package com.order.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.order.dto.InspectionLogRequest;
import com.order.entity.InspectionLog;
import com.order.service.InspectionLogService;
import com.order.util.CookieUtil;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * 検査ログ管理に関する管理者機能を提供するコントローラ
 * 検査ログの記録、履歴表示を担当します
 */
@Controller
@RequestMapping("/admin/inspection")
@RequiredArgsConstructor
public class InspectionLogController {

    private final InspectionLogService inspectionLogService;
    private final CookieUtil cookieUtil;

    /**
     * 検査フォーム画面を表示します
     * 点検対象期間の売上集計値とユーザー一覧を取得してモデルに設定します
     * 
     * @param request HTTPリクエスト（Cookie取得用）
     * @param model ビューに渡すモデル
     * @return 検査フォーム画面のテンプレート名
     */
    @GetMapping("/form")
    public String showForm(HttpServletRequest request, Model model) {
        Integer storeId = cookieUtil.getStoreIdFromCookie(request);

        // 点検対象期間の売上集計値を取得してModelに詰める
        var summary = inspectionLogService.buildInspectionSummary(storeId);
        model.addAllAttributes(summary);

        // フォーム送信用の空のリクエストオブジェクト
        model.addAttribute("inspectionLogRequest", new InspectionLogRequest());

        // ユーザー一覧（点検者選択用）
        model.addAttribute("users", inspectionLogService.getUsersForStore(storeId));

        return "admin/inspectionForm";
    }

    /**
     * 検査履歴画面を表示します
     * 
     * @param request HTTPリクエスト（Cookie取得用）
     * @param model ビューに渡すモデル
     * @return 検査履歴画面のテンプレート名
     */
    @GetMapping("/history")
    public String showHistory(HttpServletRequest request, Model model) {
        Integer storeId = cookieUtil.getStoreIdFromCookie(request);
        
        // 点検履歴を取得
        List<InspectionLog> inspectionHistory = inspectionLogService.getInspectionHistory(storeId);
        model.addAttribute("inspectionHistory", inspectionHistory);
        
        return "admin/inspectionHistory";
    }

    /**
     * 検査を登録します
     * 
     * @param request HTTPリクエスト（Cookie取得用）
     * @param inspectionLogRequest 検査ログのリクエストデータ
     * @param performWithdrawal 出金を実行するかどうか
     * @return リダイレクト先URL
     */
    @PostMapping
    public String registerInspection(HttpServletRequest request,
                                     @ModelAttribute InspectionLogRequest inspectionLogRequest,
                                     @RequestParam(name = "performWithdrawal", defaultValue = "false") boolean performWithdrawal) {
    	
        Integer storeId = cookieUtil.getStoreIdFromCookie(request);
        
        inspectionLogService.registerInspection(storeId, inspectionLogRequest, performWithdrawal);
        return "redirect:/admin/inspection/form?success";
    }
}
