package com.order.controller;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType; // ★MediaTypeをインポート
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

import com.order.dto.MenuForm; // MenuFormをインポート
import com.order.entity.Menu;
import com.order.entity.Plan;
import com.order.repository.MenuGroupRepository;
import com.order.repository.MenuTimeSlotRepository;
import com.order.repository.OptionGroupRepository;
import com.order.repository.PrinterConfigRepository;
import com.order.repository.StoreRepository; // StoreRepositoryを注入
import com.order.repository.TaxRateRepository;
import com.order.service.MenuAddService;
import com.order.service.MenuService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping(value = "/menu") // ★producesを一旦削除し、メソッドレベルで設定
public class MenuController {

    private final TaxRateRepository taxRateRepository;
    private final MenuGroupRepository menuGroupRepository;
    private final MenuTimeSlotRepository menuSlotRepository;
    private final OptionGroupRepository optionGroupRepository;
    private final PrinterConfigRepository printerConfigRepository;
    private final StoreRepository storeRepository;

    private final MenuAddService menuAddService;
    private final MenuService menuService;


    // メニュー一覧＆編集画面の表示 (HTMLを返す)
    @GetMapping(value = "/list", produces = MediaType.TEXT_HTML_VALUE) // ★HTMLを返すことを明示
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

        model.addAttribute("menus", menuAddService.getMenusByStoreId(storeId));
        model.addAttribute("timeSlots", menuSlotRepository.findByStoreStoreId(storeId));
        model.addAttribute("taxRates", taxRateRepository.findByStore_StoreId(storeId));
        model.addAttribute("menuGroups", menuAddService.getAdminMenuGroups(storeId));
        
        model.addAttribute("optionGroups", optionGroupRepository.findByStoreId(storeId));
        model.addAttribute("printers", printerConfigRepository.findByStoreId(storeId));

        List<Plan> plans = menuService.getAllPlans(storeId);
//        System.out.println("DEBUG: MenuController - Found " + plans.size() + " plans for storeId: " + storeId);
        model.addAttribute("plans", plans);

        model.addAttribute("menuForm", new MenuForm());
        model.addAttribute("storeId", storeId);

        return "menu_list_and_edit";
    }
    
    // メニューリストのデータだけをJSONで返すAPIエンドポイント
    @GetMapping(value = "/list_data", produces = MediaType.APPLICATION_JSON_VALUE) // ★JSONを返すことを明示
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
        data.put("menuGroups", menuAddService.getAdminMenuGroups(storeId));

        return new ResponseEntity<>(data, HttpStatus.OK);
    }

    // 既存メニューの詳細情報をJSONで返すAPIエンドポイント
    @GetMapping(value = "/{menuId}/details", produces = MediaType.APPLICATION_JSON_VALUE) // ★JSONを返すことを明示
    @ResponseBody
    public ResponseEntity<MenuForm> getMenuDetails(@PathVariable("menuId") Integer menuId, HttpServletRequest request) {
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

        Optional<MenuForm> optionalMenuForm = menuAddService.getMenuFormById(menuId); 
        
        if (optionalMenuForm.isPresent()) {
            MenuForm menuForm = optionalMenuForm.get();
            // StoreIDによるフィルタリングはMenuAddService.getMenuFormById()内でstoreIdを渡し、そこで処理されるべき
            // または、menuFormにstoreIdフィールドを追加して比較する
            
            return new ResponseEntity<>(menuForm, HttpStatus.OK);
        }
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }
    
    // メニューの追加・更新処理
    @PostMapping(value = "/save", produces = MediaType.APPLICATION_JSON_VALUE) // ★JSONを返すことを明示
    @ResponseBody
    public ResponseEntity<Map<String, String>> saveMenu(@ModelAttribute("menuForm") MenuForm menuForm,
    													@RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                                                        @RequestParam(value = "optionGroupIds", required = false) List<Integer> optionGroupIds,
                                                        HttpServletRequest request,
                                                        @RequestParam(value = "printerId", required = false) Integer printerId,
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

        	if (imageFile != null && !imageFile.isEmpty()) {
        	    menuImageToSave = null;
        	} else if (currentMenuImage == null || currentMenuImage.isEmpty()) {
        	    menuImageToSave = null;
        	}

            // ★MenuFormからMenuエンティティへの変換ロジックをここに書く
            Menu menu = new Menu();
            menu.setMenuId(menuForm.getMenuId());
            menu.setMenuName(menuForm.getMenuName());
            menu.setPrice(menuForm.getPrice());
            menu.setMenuDescription(menuForm.getMenuDescription());
            menu.setReceiptLabel(menuForm.getReceiptLabel());
            menu.setIsSoldOut(menuForm.getIsSoldOut());

            // 関連エンティティはIDから取得してセット
            menu.setStore(storeRepository.findById(storeId).orElseThrow(() -> new IllegalArgumentException("店舗情報が見つかりません。")));
            // DTOのフィールド名に合わせてIDを取得し、エンティティをセット
            menu.setTimeSlot(menuSlotRepository.findById(menuForm.getTimeSlotTimeSlotId()).orElse(null)); 
            menu.setTaxRate(taxRateRepository.findById(menuForm.getTaxRateTaxRateId()).orElse(null));
            menu.setMenuGroup(menuGroupRepository.findById(menuForm.getMenuGroupGroupId()).orElse(null));

            // 飲み放題関連のフィールドをセット
            menu.setIsPlanStarter(menuForm.getIsPlanStarter());
            menu.setPlanId(menuForm.getPlanId());

            if (menu.getMenuId() == null) {
                menuAddService.addNewMenu(menu, imageFile, menuImageToSave, optionGroupIds, printerId, storeId);
                response.put("status", "success");
                response.put("message", "メニューを追加しました！");
            } else {
                menuAddService.updateExistingMenu(menu, imageFile, menuImageToSave, optionGroupIds, printerId, storeId);
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
    @PostMapping(value = "/delete/{menuId}", produces = MediaType.APPLICATION_JSON_VALUE) // ★JSONを返すことを明示
    @ResponseBody
    public ResponseEntity<Map<String, String>> deleteMenu(@PathVariable("menuId") Integer menuId,
                                                          HttpServletRequest request) {
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
    
    
    //売り切れ登録画面
    @GetMapping("/sold-out-management")
    public String showSoldOutManagementPage(HttpServletRequest request, Model model) {
        // JSと同じようにCookieからstoreIdを取得する処理を入れておくといいね
        Integer storeId = getStoreIdFromCookie(request);

        if (storeId == null) {
            // 店舗IDが取得できない場合は、エラーページやログインページにリダイレクトするなどの処理を検討してもいいかも
            // ここでは簡易的にエラーメッセージをModelに追加してるけど、実運用ではもっと丁寧なエラーハンドリングが必要だよ
            model.addAttribute("errorMessage", "店舗IDが取得できませんでした。ログインし直してください。");
            return "error"; // 例えば、エラー表示用のHTMLに遷移させるとか
        }
        
        // ModelにstoreIdを追加しておくと、HTML側でJavaScriptに渡すときに便利だよ
        // 例えば <input type="hidden" id="storeId" th:value="${storeId}"> みたいに
        model.addAttribute("storeId", storeId);

        // "menu_sold_out"という文字列を返すことで、
        // Spring Bootはsrc/main/resources/templates/menu_sold_out.htmlを探して表示してくれるよ
        return "menu_sold_out";
    }

    // CookieからstoreIdを取得するヘルパーメソッド（MenuSoldOutControllerと内容は同じでOK）
    private Integer getStoreIdFromCookie(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("storeId".equals(cookie.getName())) {
                    try {
                        return Integer.parseInt(cookie.getValue());
                    } catch (NumberFormatException e) {
                        return null;
                    }
                }
            }
        }
        return null;
    }
}