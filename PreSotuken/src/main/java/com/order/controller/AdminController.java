package com.order.controller;

//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.order.dto.TerminalCreationDto;
import com.order.entity.Logo;
import com.order.entity.Seat;
import com.order.entity.Store;
import com.order.entity.Terminal;
import com.order.repository.SeatRepository;
import com.order.repository.StoreRepository;
import com.order.repository.TerminalRepository;
import com.order.service.LogoService;
import com.order.service.StoreService;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController { // クラス名はAdminControllerではなくAdminTerminalControllerにした方が役割が明確

    private final TerminalRepository terminalRepository;
    private final SeatRepository seatRepository;
    private final StoreRepository storeRepository;
    private final LogoService logoService;
    private final StoreService storeService;


    @GetMapping("/terminals")
    public String showTerminalManagementPage(Model model) {
        return "admin_terminals"; // src/main/resources/templates/admin_terminals.html を参照
    }

    @PostMapping("/terminals")
    @ResponseBody
    public ResponseEntity<Map<String, String>> addTerminal(@RequestBody TerminalCreationDto dto,
                                                           @CookieValue("storeId") Integer storeId) {
        Map<String, String> response = new HashMap<>();

        if (storeId == null) {
            response.put("message", "店舗IDが指定されていません。");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        Store store = storeRepository.findById(storeId).orElse(null);
        if (store == null) {
            response.put("message", "指定された店舗が見つかりません。");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        // 管理者端末の場合は座席IDが不要になるため、nullチェックをdto.isAdmin()で条件分岐
        Seat seat = null;
        if (!dto.isAdmin()) { // 管理者端末ではない場合のみ座席IDが必須
            if (dto.getSeatId() == null) {
                response.put("message", "座席IDが指定されていません。");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            seat = seatRepository.findById(dto.getSeatId()).orElse(null);
            if (seat == null) {
                response.put("message", "指定された座席が見つかりません。");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            // 座席が現在の店舗に属しているかのチェックも管理者端末ではない場合のみ
            if (!seat.getStore().getStoreId().equals(storeId)) {
                response.put("message", "選択された座席は現在の店舗に属していません。");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
        }
        // 管理者端末の場合はseatはnullのまま、または適切に処理されるようにする


        Terminal newTerminal = new Terminal();
        newTerminal.setSeat(seat); // 管理者端末の場合はnullになる可能性がある
        newTerminal.setStore(store);
        
        String ipAddress = dto.getIpAddress();
        // 管理者端末かどうかに関わらずIPアドレスは常に必須
        if (ipAddress == null || ipAddress.isEmpty()) {
            response.put("message", "IPアドレスが不正です。");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
        newTerminal.setIpAddress(ipAddress);

        newTerminal.setAdmin(dto.isAdmin()); // DTOからisAdminの値を取得してエンティティに設定

        try {
            terminalRepository.save(newTerminal);
            response.put("message", "端末が正常に追加されました。");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("端末追加中にエラーが発生しました: " + e.getMessage());
            response.put("message", "端末の追加に失敗しました。");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    // 店舗編集フォームを表示する
    // storeId を Cookie から取得するように変更
    @GetMapping("/store/edit") // パス変数からCookie取得に変更したので、URLからIDを削除
    public String showEditForm(
            @CookieValue(name = "storeId", required = false) Integer storeId, // ここを修正！
            Model model) {

        if (storeId == null) {
            // Cookieに storeId がない場合のエラーハンドリング
            model.addAttribute("store", new Store());
            model.addAttribute("errorMessage", "店舗IDがCookieから見つかりませんでした。");
            return "storeEdit";
        }

        storeService.getStoreById(storeId).ifPresentOrElse(
            store -> model.addAttribute("store", store),
            () -> {
                // CookieのIDで店舗が見つからなかった場合
                model.addAttribute("store", new Store());
                model.addAttribute("errorMessage", "指定された店舗（ID: " + storeId + "）が見つかりませんでした。");
            }
        );
        return "storeEdit"; // storeEdit.html を表示
    }

    // 店舗情報を更新する
    @PostMapping("/store/edit")
    public String updateStore(@ModelAttribute Store store, RedirectAttributes redirectAttributes) {
        // 更新対象の店舗IDがセットされているか確認
        // ここはフォームから hidden フィールドで storeId が送られてくるので、CookieValueは不要だよ
        if (store.getStoreId() == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "店舗IDが指定されていません。");
            return "redirect:/admin/store/edit"; // Cookieから取得するので、IDなしのURLにリダイレクト
        }

        storeService.updateStore(store);
        redirectAttributes.addFlashAttribute("successMessage", "店舗情報を更新しました！");
        return "redirect:/admin/store/edit"; // 更新後もCookieのIDを使うので、IDなしのURLにリダイレクト
    }
    
    // 端末編集 (PUT) API
    // DTOは追加時と同じTerminalCreationDtoを使うか、TerminalエンティティをRequestBodyとして受け取る
    // ここではTerminalCreationDtoを再利用し、IDはパス変数から取得する
    @PutMapping("/terminals/{terminalId}")
    @ResponseBody
    public ResponseEntity<Map<String, String>> updateTerminal(@PathVariable Integer terminalId,
                                                              @RequestBody TerminalCreationDto dto, // 更新用のDTO
                                                              @CookieValue("storeId") Integer storeId) {
        Map<String, String> response = new HashMap<>();

        if (storeId == null) {
            response.put("message", "店舗IDが指定されていません。");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
        
        Optional<Terminal> existingTerminalOpt = terminalRepository.findById(terminalId);
        if (existingTerminalOpt.isEmpty()) {
            response.put("message", "指定された端末が見つかりません。");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
        Terminal existingTerminal = existingTerminalOpt.get();

        // 端末が現在の店舗に属しているか確認
        if (!existingTerminal.getStore().getStoreId().equals(storeId)) {
            response.put("message", "この端末を編集する権限がありません。");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response); // 403 Forbidden
        }

        // 座席の更新 (seatIdが変更された場合)
        Seat seat = seatRepository.findById(dto.getSeatId()).orElse(null);
        if (seat == null) {
            response.put("message", "指定された座席が見つかりません。");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
        // 更新後の座席も現在の店舗に属しているか確認
        if (!seat.getStore().getStoreId().equals(storeId)) {
            response.put("message", "選択された座席は現在の店舗に属していません。");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
        existingTerminal.setSeat(seat);


        // IPアドレスの更新
        String ipAddress = dto.getIpAddress(); // DTOから直接IPアドレスを取得
        if (ipAddress == null || ipAddress.isEmpty()) { // nullチェックも追加
            response.put("message", "IPアドレスが不正です。");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        try {
            terminalRepository.save(existingTerminal); // saveで更新
            response.put("message", "端末情報が正常に更新されました。");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("端末更新中にエラーが発生しました: " + e.getMessage());
            response.put("message", "端末の更新に失敗しました。");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }


    // 端末削除 (DELETE) API
    @DeleteMapping("/terminals/{terminalId}")
    @ResponseBody
    public ResponseEntity<Map<String, String>> deleteTerminal(@PathVariable Integer terminalId,
                                                              @CookieValue("storeId") Integer storeId) {
        Map<String, String> response = new HashMap<>();

        if (storeId == null) {
            response.put("message", "店舗IDが指定されていません。");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        Optional<Terminal> existingTerminalOpt = terminalRepository.findById(terminalId);
        if (existingTerminalOpt.isEmpty()) {
            response.put("message", "指定された端末が見つかりません。");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
        Terminal existingTerminal = existingTerminalOpt.get();

        // 端末が現在の店舗に属しているか確認 (重要: 他店舗のデータを削除させない)
        if (!existingTerminal.getStore().getStoreId().equals(storeId)) {
            response.put("message", "この端末を削除する権限がありません。");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response); // 403 Forbidden
        }

        try {
            terminalRepository.delete(existingTerminal); // エンティティを渡して削除
            response.put("message", "端末が正常に削除されました。");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("端末削除中にエラーが発生しました: " + e.getMessage());
            response.put("message", "端末の削除に失敗しました。");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/terminals/seats")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getSeatsByStoreId(@CookieValue("storeId") Integer storeId) {
        if (storeId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        
        Store store = storeRepository.findById(storeId).orElse(null);
        if (store == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        List<Seat> seats = seatRepository.findByStore_StoreId(store.getStoreId());
        
        List<Map<String, Object>> seatList = seats.stream()
            .map(seat -> {
                Map<String, Object> seatMap = new HashMap<>();
                seatMap.put("seatId", seat.getSeatId());
                seatMap.put("seatName", seat.getSeatName());
                return seatMap;
            })
            .collect(Collectors.toList());

        return ResponseEntity.ok(seatList);
    }

    /**
     * 既存の端末リストを取得します。（管理画面表示用API）
     * GET /admin/terminals/list にマッピングを変更
     * @param storeId 店舗ID
     * @return 端末情報のリスト
     */
    @GetMapping("/terminals/list")
    @ResponseBody
    public ResponseEntity<List<Terminal>> getTerminalsByStoreId(@CookieValue("storeId") Integer storeId) {
        if (storeId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        List<Terminal> terminals = terminalRepository.findByStoreStoreId(storeId); 
        return ResponseEntity.ok(terminals);
    }
    
    
    
    
    /**
     * ロゴ設定画面を表示する。
     * 既存のロゴがあれば表示し、なければデフォルト画像を示す。
     */
    @GetMapping("/terminals/logo")
    public String showLogoSettingPage(Model model) {
        Long storeId = 1L; // 例: 固定の店舗ID。陽翔君のシステムに合わせて調整してね！

        Optional<Logo> logoOptional = logoService.findLogoByStoreId(storeId);

        if (logoOptional.isPresent()) {
            model.addAttribute("logoExists", true);
            model.addAttribute("logoDataUri", "data:image/png;base64," + logoOptional.get().getLogoData());
        } else {
            model.addAttribute("logoExists", false);
            model.addAttribute("defaultLogoPath", "/images/default_logo.png");
        }

        if (model.asMap().containsKey("successMessage")) {
            model.addAttribute("successMessage", model.asMap().get("successMessage"));
        }
        if (model.asMap().containsKey("errorMessage")) {
            model.addAttribute("errorMessage", model.asMap().get("errorMessage"));
        }

        return "logoSetting"; // templates/admin/logoSetting.html を表示する想定
    }

    /**
     * ロゴ画像を保存または更新する。
     * フロントエンドからBASE64エンコードされた文字列を受け取る。
     */
    @PostMapping("/terminals/logo/upload")
    public String uploadLogo(@RequestParam("storeId") Long storeId,
                             @RequestParam("logoBase64") String logoBase64,
                             RedirectAttributes redirectAttributes) {

        if (logoBase64 == null || logoBase64.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "ロゴデータが空です。");
            return "redirect:/admin/terminals/logo";
        }

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

        return "redirect:/admin/terminals/logo";
    }
    
}