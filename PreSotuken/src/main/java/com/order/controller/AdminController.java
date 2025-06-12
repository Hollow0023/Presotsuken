package com.order.controller;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.order.dto.TerminalCreationDto;
import com.order.entity.Seat;
import com.order.entity.Store;
import com.order.entity.Terminal;
import com.order.repository.SeatRepository;
import com.order.repository.StoreRepository;
import com.order.repository.TerminalRepository;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/admin/terminals")
@RequiredArgsConstructor
public class AdminController { // クラス名はAdminControllerではなくAdminTerminalControllerにした方が役割が明確

    private final TerminalRepository terminalRepository;
    private final SeatRepository seatRepository;
    private final StoreRepository storeRepository;

    @GetMapping
    public String showTerminalManagementPage(Model model) {
        return "admin_terminals"; // src/main/resources/templates/admin_terminals.html を参照
    }

    @PostMapping
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

        Seat seat = seatRepository.findById(dto.getSeatId()).orElse(null);
        if (seat == null) {
            response.put("message", "指定された座席が見つかりません。");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
        
        if (!seat.getStore().getStoreId().equals(storeId)) {
            response.put("message", "選択された座席は現在の店舗に属していません。");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        Terminal newTerminal = new Terminal();
        newTerminal.setSeat(seat);
        newTerminal.setStore(store);
     // 修正後のaddTerminalの該当部分
        String ipAddress = dto.getIpAddress(); // DTOから直接IPアドレスを取得
        if (ipAddress == null || ipAddress.isEmpty()) { // nullチェックも追加
            response.put("message", "IPアドレスが不正です。");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
        newTerminal.setIpAddress(ipAddress); // DTOから取得したIPアドレスを設定

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
    
    // ★★★ 端末編集 (PUT) API ★★★
    // DTOは追加時と同じTerminalCreationDtoを使うか、またはTerminalエンティティをRequestBodyとして直接受け取る
    // ここではIDを含めて更新するため、TerminalCreationDtoUpdateなどの新しいDTOを作るか、
    // あるいはTerminalエンティティを直接RequestBodyとして受け取るのがシンプル。
    // 仮にTerminalエンティティを直接受け取るとして、IDはパス変数から取得する
    @PutMapping("/{terminalId}") // ★ PUT /admin/terminals/{terminalId}
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


    // ★★★ 端末削除 (DELETE) API ★★★
    @DeleteMapping("/{terminalId}") // ★ DELETE /admin/terminals/{terminalId}
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

    @GetMapping("/seats")
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
    @GetMapping("/list") // ★★★ ここを /list に変更！ ★★★
    @ResponseBody
    public ResponseEntity<List<Terminal>> getTerminalsByStoreId(@CookieValue("storeId") Integer storeId) {
        if (storeId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        List<Terminal> terminals = terminalRepository.findByStoreStoreId(storeId); 
        return ResponseEntity.ok(terminals);
    }
}