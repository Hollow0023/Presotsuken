package com.order.controller;

import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.order.entity.Logo;
import com.order.service.LogoService;

import lombok.RequiredArgsConstructor;

/**
 * ロゴ管理に関する管理者機能を提供するコントローラ
 * ロゴの表示、アップロードを担当します
 */
@Controller
@RequestMapping("/admin/logo")
@RequiredArgsConstructor
public class AdminLogoController {

    private final LogoService logoService;

    /**
     * ロゴ設定画面を表示します
     * 既存のロゴがあれば表示し、なければデフォルト画像を示します
     * 
     * @param model ビューに渡すモデル
     * @return ロゴ設定画面のテンプレート名
     */
    @GetMapping
    public String showLogoSettingPage(Model model) {
        // FIXME: 固定の店舗ID。実際のシステムでは適切な方法で取得する必要があります
        Long storeId = 1L;

        Optional<Logo> logoOptional = logoService.findLogoByStoreId(storeId);

        if (logoOptional.isPresent()) {
            model.addAttribute("logoExists", true);
            model.addAttribute("logoDataUri", "data:image/png;base64," + logoOptional.get().getLogoData());
        } else {
            model.addAttribute("logoExists", false);
            model.addAttribute("defaultLogoPath", "/images/default_logo.png");
        }

        // フラッシュメッセージがあれば設定
        if (model.asMap().containsKey("successMessage")) {
            model.addAttribute("successMessage", model.asMap().get("successMessage"));
        }
        if (model.asMap().containsKey("errorMessage")) {
            model.addAttribute("errorMessage", model.asMap().get("errorMessage"));
        }

        return "logoSetting";
    }

    /**
     * ロゴ画像を保存または更新します
     * フロントエンドからBASE64エンコードされた文字列を受け取ります
     * 
     * @param storeId 店舗ID
     * @param logoBase64 BASE64エンコードされたロゴデータ
     * @param redirectAttributes リダイレクト先に渡す属性
     * @return リダイレクト先URL
     */
    @PostMapping("/upload")
    public String uploadLogo(@RequestParam("storeId") Long storeId,
                             @RequestParam("logoBase64") String logoBase64,
                             RedirectAttributes redirectAttributes) {

        if (logoBase64 == null || logoBase64.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "ロゴデータが空です。");
            return "redirect:/admin/logo";
        }

        // データURIのプレフィックスを除去してBASE64データのみを抽出
        String cleanedBase64Data = logoBase64;
        if (logoBase64.startsWith("data:")) {
            int commaIndex = logoBase64.indexOf(',');
            if (commaIndex != -1) {
                cleanedBase64Data = logoBase64.substring(commaIndex + 1);
            }
        }

        try {
            logoService.saveOrUpdateLogo(storeId, cleanedBase64Data);
            redirectAttributes.addFlashAttribute("successMessage", "ロゴが正常に保存されました！");
        } catch (Exception e) {
            System.err.println("ロゴの保存中にエラーが発生しました: " + e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "ロゴの保存中にエラーが発生しました。");
        }

        return "redirect:/admin/logo";
    }
}