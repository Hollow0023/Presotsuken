// src/main/java/com/order/controller/MenuController.java (修正版)

package com.order.controller;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.order.entity.Menu;
import com.order.entity.MenuGroup;
import com.order.entity.MenuTimeSlot;
import com.order.entity.OptionGroup;
import com.order.entity.PrinterConfig;
import com.order.entity.TaxRate;
import com.order.repository.MenuGroupRepository;
import com.order.repository.MenuTimeSlotRepository;
import com.order.repository.OptionGroupRepository;
import com.order.repository.PrinterConfigRepository;
import com.order.repository.TaxRateRepository;
import com.order.service.MenuAddService; // MenuAddServiceを使用
import com.order.service.MenuService; // ★ 追加：MenuServiceをインポート

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

// JSONシリアライゼーションの無限ループ対策のためにJacksonのアノテーションをインポート
// import com.fasterxml.jackson.annotation.JsonManagedReference;
// import com.fasterxml.jackson.annotation.JsonBackReference;

@Controller
@RequiredArgsConstructor
@RequestMapping("/menu")
public class MenuController {

    private final TaxRateRepository taxRateRepository;
    private final MenuGroupRepository menuGroupRepository; // 既存
    private final MenuTimeSlotRepository menuSlotRepository;
    private final OptionGroupRepository optionGroupRepository;
    private final PrinterConfigRepository printerConfigRepository;

    private final MenuAddService menuAddService; // 既存
    private final MenuService menuService; // ★ 追加：MenuServiceを注入

    // メニュー一覧＆編集画面の表示 (管理者用注文画面も兼ねる想定)
    @GetMapping("/list")
    public String showMenuListAndEditForm(HttpServletRequest request, Model model) {
        Integer storeId = null;
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("storeId".equals(cookie.getName())) {
                    try {
                        storeId = Integer.parseInt(cookie.getValue());
                        break;
                    } catch (NumberFormatException e) {
                        return "redirect:/error?message=Invalid store ID in cookie";
                    }
                }
            }
        }

        if (storeId == null) {
            return "redirect:/login";
        }

        List<Menu> menus = menuAddService.getMenusByStoreId(storeId);
        model.addAttribute("menus", menus);

        List<TaxRate> taxRates = taxRateRepository.findByStore_StoreId(storeId);
        
        // ★ 修正：MenuAddServiceのgetAdminMenuGroupsを呼び出す
        // MenuAddServiceにgetAdminMenuGroupsを追加する必要があるよ
        List<MenuGroup> menuGroups = menuAddService.getAdminMenuGroups(storeId); 
        
        List<MenuTimeSlot> timeSlots = menuSlotRepository.findByStoreStoreId(storeId);
        List<OptionGroup> optionGroups = optionGroupRepository.findByStoreId(storeId);
        List<PrinterConfig> printers = printerConfigRepository.findByStoreId(storeId);

        model.addAttribute("menuForm", new Menu());
        model.addAttribute("taxRates", taxRates);
        model.addAttribute("menuGroups", menuGroups); // 修正されたリストが渡される
        model.addAttribute("timeSlots", timeSlots);
        model.addAttribute("optionGroups", optionGroups);
        model.addAttribute("printers", printers);

        return "menu_list_and_edit";
    }
    
    // メニューリストのデータだけをJSONで返すAPIエンドポイント
    @GetMapping("/list_data")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getMenuListData(HttpServletRequest request) {
        Integer storeId = null;
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("storeId".equals(cookie.getName())) {
                    try {
                        storeId = Integer.parseInt(cookie.getValue());
                        break;
                    } catch (NumberFormatException e) {
                        return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
                    }
                }
            }
        }
        if (storeId == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("menus", menuAddService.getMenusByStoreId(storeId));
        // ★ 修正：MenuAddServiceのgetAdminMenuGroupsを呼び出す
        // MenuAddServiceにgetAdminMenuGroupsを追加する必要があるよ
        data.put("menuGroups", menuAddService.getAdminMenuGroups(storeId)); 

        return new ResponseEntity<>(data, HttpStatus.OK);
    }

    // 既存メニューの詳細情報をJSONで返すAPIエンドポイント
    @GetMapping("/{menuId}/details")
    @ResponseBody
    public ResponseEntity<Menu> getMenuDetails(@PathVariable("menuId") Integer menuId, HttpServletRequest request) {
        Integer storeId = null;
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("storeId".equals(cookie.getName())) {
                    storeId = Integer.parseInt(cookie.getValue());
                    break;
                }
            }
        }
        if (storeId == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        // ここでMenuServiceからメニュー詳細を取得
        Optional<Menu> optionalMenu = menuAddService.getMenuById(menuId);
        
        if (optionalMenu.isPresent()) {
            Menu menu = optionalMenu.get();
            // 取得したメニューが現在の店舗に属しているか確認
            if (!menu.getStore().getStoreId().equals(storeId)) {
                return new ResponseEntity<>(HttpStatus.FORBIDDEN); // 403 Forbidden
            }
            
            // 関連エンティティ（MenuOption, MenuPrinterMap）がEager Loadingされているか、
            // または、ここで明示的に初期化する必要がある。
            // 例えば、menu.getMenuOptions().size(); や menu.getMenuPrinterMaps().size();
            // もしくは、fetch join を使用したRepositoryメソッドを作成する。
            // あるいは、DTOに変換して返す。
            // ★ここではMenuエンティティに@OneToMany(fetch = FetchType.EAGER)を追加し、
            //   @JsonManagedReference/@JsonBackReferenceを正しく設定している前提とする。
            return new ResponseEntity<>(menu, HttpStatus.OK);
        }
        return new ResponseEntity<>(HttpStatus.NOT_FOUND); // 404 Not Found
    }
    
    // 品切れ登録ページを表示するためのエンドポイント
    @GetMapping("/sold-out-management")
    public String showSoldOutManagementPage(HttpServletRequest request, Model model) {
        Integer storeId = null;
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("storeId".equals(cookie.getName())) {
                    try {
                        storeId = Integer.parseInt(cookie.getValue());
                        break;
                    } catch (NumberFormatException e) {
                        return "redirect:/error?message=Invalid store ID in cookie";
                    }
                }
            }
        }

        if (storeId == null) {
            return "redirect:/login";
        }
        // ここで特にmodelにデータを追加する必要はない（JavaScriptでAPIから取得するため）
        return "menu_sold_out";
    }

    // メニューの追加・更新処理
    @PostMapping("/save")
    @ResponseBody
    public ResponseEntity<Map<String, String>> saveMenu(@ModelAttribute("menuForm") Menu menu,
                                                        @RequestParam("imageFile") MultipartFile imageFile,
                                                        @RequestParam(value = "optionGroupIds", required = false) List<Integer> optionGroupIds,
                                                        HttpServletRequest request,
                                                        @RequestParam(value = "printerIds", required = false) List<Integer> printerIds,
                                                        @RequestParam(value = "currentMenuImage", required = false) String currentMenuImage) {

        Integer storeId = null;
        for (Cookie cookie : request.getCookies()) {
            if ("storeId".equals(cookie.getName())) {
                storeId = Integer.parseInt(cookie.getValue());
                break;
            }
        }
        if (storeId == null) {
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "店舗情報が取得できませんでした。");
            return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
        }

        Map<String, String> response = new HashMap<>();
        try {
            String menuImageToSave = currentMenuImage;

            if (!imageFile.isEmpty()) {
                menuImageToSave = null;
            } else if (currentMenuImage == null || currentMenuImage.isEmpty()) {
                menuImageToSave = null;
            }

            if (menu.getMenuId() == null) {
                menuAddService.addNewMenu(menu, imageFile, menuImageToSave, optionGroupIds, printerIds, storeId);
                response.put("status", "success");
                response.put("message", "メニューを追加しました！");
            } else {
                menuAddService.updateExistingMenu(menu, imageFile, menuImageToSave, optionGroupIds, printerIds, storeId);
                response.put("status", "success");
                response.put("message", "メニューを更新しました！");
            }
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        } catch (IOException e) {
            response.put("status", "error");
            response.put("message", "ファイルのアップロード中にエラーが発生しました。");
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "メニューの保存に失敗しました。");
            e.printStackTrace();
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // メニュー削除エンドポイント
    @PostMapping("/delete/{menuId}")
    @ResponseBody
    public ResponseEntity<Map<String, String>> deleteMenu(@PathVariable("menuId") Integer menuId,
                                                          HttpServletRequest request) {
        Integer storeId = null;
        for (Cookie cookie : request.getCookies()) {
            if ("storeId".equals(cookie.getName())) {
                storeId = Integer.parseInt(cookie.getValue());
                break;
            }
        }
        if (storeId == null) {
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "店舗情報が取得できませんでした。");
            return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
        }
        
        Map<String, String> response = new HashMap<>();
        try {
            menuAddService.deleteMenu(menuId, storeId);
            response.put("status", "success");
            response.put("message", "メニューを削除しました！");
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "メニューの削除に失敗しました。");
            e.printStackTrace();
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}