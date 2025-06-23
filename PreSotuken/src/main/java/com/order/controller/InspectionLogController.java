package com.order.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.order.dto.InspectionLogRequest;
import com.order.service.CookieUtil;
import com.order.service.InspectionLogService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/admin/inspection")
@RequiredArgsConstructor
public class InspectionLogController {

    private final InspectionLogService inspectionLogService;
    private final CookieUtil cookieUtil;

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

    @PostMapping
    public String registerInspection(HttpServletRequest request,
                                     @ModelAttribute InspectionLogRequest inspectionLogRequest) {
        Integer storeId = cookieUtil.getStoreIdFromCookie(request);
        inspectionLogService.registerInspection(storeId, inspectionLogRequest);
        return "redirect:/admin/inspection/form?success";
    }
}
