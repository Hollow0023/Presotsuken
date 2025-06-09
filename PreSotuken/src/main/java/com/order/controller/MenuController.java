package com.order.controller;

import java.io.IOException;
import java.util.List;
// import java.util.Optional; // MenuServiceに移ったので不要
// import java.util.stream.Collectors; // MenuServiceに移ったので不要

import org.springframework.stereotype.Controller;
// import org.springframework.transaction.annotation.Transactional; // Serviceに移ったので不要
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.order.entity.Menu;
import com.order.entity.MenuGroup;
import com.order.entity.MenuTimeSlot;
import com.order.entity.PrinterConfig;
// import com.order.entity.Store; // MenuServiceに移ったので不要
import com.order.entity.TaxRate;
import com.order.repository.MenuGroupRepository;
// import com.order.repository.MenuOptionRepository; // Serviceに移ったので不要
// import com.order.repository.MenuPrinterMapRepository; // Serviceに移ったので不要
// import com.order.repository.MenuRepository; // Serviceに移ったので不要
import com.order.repository.MenuTimeSlotRepository;
import com.order.repository.OptionGroupRepository;
import com.order.repository.PrinterConfigRepository; // @GetMappingでプリンタリストを取得するために残す
// import com.order.repository.StoreRepository; // Serviceに移ったので不要
import com.order.repository.TaxRateRepository;
import com.order.service.MenuAddService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/menu")
public class MenuController {

    // データ取得系はControllerに残してもOK、または別途Serviceに移す
    private final TaxRateRepository taxRateRepository;
    private final MenuGroupRepository menuGroupRepository;
    // private final StoreRepository storeRepository; // MenuServiceで使うので削除
    private final MenuTimeSlotRepository menuSlotRepository;
    private final OptionGroupRepository optionGroupRepository;
    private final PrinterConfigRepository printerConfigRepository; // GETリクエストで必要なので残す

    // ★MenuServiceをDIする
    private final MenuAddService menuAddService;

    @GetMapping("/add")
    public String showAddMenuForm(HttpServletRequest request, Model model) {
        Integer storeId = null;
        for (Cookie cookie : request.getCookies()) {
            if ("storeId".equals(cookie.getName())) {
                storeId = Integer.parseInt(cookie.getValue());
                break;
            }
        }

        if (storeId == null) {
            return "redirect:/login"; // storeIdがない場合はログインページへ
        }

        // 各種リストの取得
        List<TaxRate> taxRates = taxRateRepository.findByStore_StoreId(storeId);
        List<MenuGroup> menuGroups = menuGroupRepository.findByStore_StoreId(storeId);
        List<MenuTimeSlot> timeSlots = menuSlotRepository.findByStoreStoreId(storeId);
        List<PrinterConfig> printers = printerConfigRepository.findByStoreId(storeId); // PrinterConfigエンティティの関連付けに合わせて調整してね

        model.addAttribute("optionGroups", optionGroupRepository.findByStoreId(storeId));
        model.addAttribute("menu", new Menu()); // 新規作成用の空のMenuオブジェクト
        model.addAttribute("taxRates", taxRates);
        model.addAttribute("menuGroups", menuGroups);
        model.addAttribute("timeSlots", timeSlots);
        model.addAttribute("printers", printers); // プリンタリストを追加

        return "menu_add";
    }

    @PostMapping("/add")
    // @Transactional はServiceに移したので、Controllerからは削除
    public String addMenu(@ModelAttribute Menu menu,
                          @RequestParam("imageFile") MultipartFile imageFile,
                          @RequestParam(required = false) List<Integer> optionGroupIds,
                          HttpServletRequest request,
                          @RequestParam(value = "printerIds", required = false) List<Integer> printerIds,
                          RedirectAttributes redirectAttributes) throws IOException {

        Integer storeId = null;
        for (Cookie cookie : request.getCookies()) {
            if ("storeId".equals(cookie.getName())) {
                storeId = Integer.parseInt(cookie.getValue());
                break;
            }
        }
        if (storeId == null) {
            redirectAttributes.addFlashAttribute("error", "店舗情報が取得できませんでした。");
            return "redirect:/login";
        }

        try {
            // ★Serviceのメソッドを呼び出す！
            menuAddService.addNewMenu(menu, imageFile, optionGroupIds, printerIds, storeId);
            redirectAttributes.addFlashAttribute("success", "メニューを追加しました！");
        } catch (IllegalArgumentException e) {
            // Serviceからスローされた業務ロジック上のエラー
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (IOException e) {
            // 画像アップロード時のIOException
            redirectAttributes.addFlashAttribute("error", "ファイルのアップロード中にエラーが発生しました。");
        } catch (Exception e) {
            // その他の予期せぬエラー
            redirectAttributes.addFlashAttribute("error", "メニューの追加に失敗しました。");
            e.printStackTrace(); // デバッグ用にスタックトレースを出力
        }
        
        return "redirect:/menu/add";
    }
}