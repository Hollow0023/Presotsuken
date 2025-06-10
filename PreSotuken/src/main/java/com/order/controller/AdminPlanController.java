package com.order.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.order.dto.PlanRequestDto;
import com.order.dto.PlanResponseDto;
import com.order.entity.MenuGroup;
import com.order.repository.MenuGroupRepository;
import com.order.repository.StoreRepository;
import com.order.service.PlanService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/plans")
public class AdminPlanController {

    private final PlanService planService;
    private final MenuGroupRepository menuGroupRepository;
    private final StoreRepository storeRepository;

    // プラン管理の単一画面表示
    @GetMapping
    public String managePlans(
            @CookieValue("storeId") Integer storeId, // ★修正：Cookieから取得
            Model model) {

        // Storeエンティティがnullの場合を考慮して、Serviceに渡す前にチェック
        if (storeId == null) {
            // storeIdがCookieにない場合のハンドリング（例: エラーページへリダイレクトなど）
            // 現状は、管理画面の入り口でstoreIdがCookieに設定されていることを前提とする
            return "redirect:/error?message=storeId is missing"; // 仮のリダイレクト先
        }

        List<PlanResponseDto> plans = planService.getAllPlans(storeId);
        model.addAttribute("plans", plans);

        List<MenuGroup> allMenuGroups = menuGroupRepository.findByStore_StoreIdOrderBySortOrderAsc(storeId);
        model.addAttribute("allMenuGroups", allMenuGroups);

        PlanRequestDto newPlanRequestDto = new PlanRequestDto();
        newPlanRequestDto.setStoreId(storeId);
        model.addAttribute("planForm", newPlanRequestDto);

        model.addAttribute("storeId", storeId); // HTML表示用にstoreIdをModelに渡す
        return "plan_manager";
    }

    // プランの新規作成処理
    @PostMapping("/create")
    public String createPlan(
            @ModelAttribute PlanRequestDto requestDto,
            @CookieValue("storeId") Integer storeId, // ★修正：Cookieから取得
            RedirectAttributes redirectAttributes) {

        if (storeId == null) {
            return "redirect:/error?message=storeId is missing";
        }
        requestDto.setStoreId(storeId); // Cookieから取得したstoreIdをDTOに上書きセット

        planService.createPlan(requestDto);
        redirectAttributes.addFlashAttribute("message", "プランが正常に作成されました。");
        return "redirect:/admin/plans"; // ★修正：storeIdパラメータを削除
    }

    // プランの更新処理
    @PostMapping("/update")
    public String updatePlan(
            @ModelAttribute PlanRequestDto requestDto,
            @CookieValue("storeId") Integer storeId, // ★修正：Cookieから取得
            RedirectAttributes redirectAttributes) {

        if (storeId == null) {
            return "redirect:/error?message=storeId is missing";
        }
        requestDto.setStoreId(storeId); // Cookieから取得したstoreIdをDTOに上書きセット

        planService.updatePlan(requestDto);
        redirectAttributes.addFlashAttribute("message", "プランが正常に更新されました。");
        return "redirect:/admin/plans"; // ★修正：storeIdパラメータを削除
    }

    // プランの削除処理
    @PostMapping("/delete/{planId}")
    public String deletePlan(
            @PathVariable Integer planId,
            @CookieValue("storeId") Integer storeId, // ★修正：Cookieから取得
            RedirectAttributes redirectAttributes) {

        if (storeId == null) {
            return "redirect:/error?message=storeId is missing";
        }

        planService.deletePlan(planId);
        redirectAttributes.addFlashAttribute("message", "プランが正常に削除されました。");
        return "redirect:/admin/plans"; // ★修正：storeIdパラメータを削除
    }

    // 編集ボタンクリック時に、JSON形式でプランデータを返すAPI (JavaScript用)
    @GetMapping("/api/{planId}")
    @ResponseBody
    public PlanResponseDto getPlanJson(@PathVariable Integer planId) {
        // このAPIはstoreIdに依存しないため、変更なし
        return planService.getPlanById(planId)
                .orElseThrow(() -> new IllegalArgumentException("指定されたプランが見つかりません。"));
    }
}