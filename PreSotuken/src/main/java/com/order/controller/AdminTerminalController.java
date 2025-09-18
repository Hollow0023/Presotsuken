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
import com.order.service.TerminalService;

import lombok.RequiredArgsConstructor;

/**
 * 端末管理に関する管理者機能を提供するコントローラ
 * 端末の追加、編集、削除、一覧表示を担当します
 */
@Controller
@RequestMapping("/admin/terminals")
@RequiredArgsConstructor
public class AdminTerminalController {

    private final TerminalService terminalService;

    /**
     * 端末管理画面を表示します
     * 
     * @param model ビューに渡すモデル
     * @return 端末管理画面のテンプレート名
     */
    @GetMapping
    public String showTerminalManagementPage(Model model) {
        return "admin_terminals";
    }

    /**
     * 新しい端末を追加します
     * 
     * @param dto 端末作成用DTO
     * @param storeId 店舗ID（Cookieから取得）
     * @return 処理結果のレスponse
     */
    @PostMapping
    @ResponseBody
    public ResponseEntity<Map<String, String>> addTerminal(@RequestBody TerminalCreationDto dto,
                                                           @CookieValue("storeId") Integer storeId) {
        try {
            terminalService.createTerminal(dto, storeId);
            Map<String, String> response = new HashMap<>();
            response.put("message", "端末が正常に追加されました。");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, String> response = new HashMap<>();
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        } catch (Exception e) {
            Map<String, String> response = new HashMap<>();
            response.put("message", "端末の追加に失敗しました。");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 端末情報を更新します
     * 
     * @param terminalId 端末ID
     * @param dto 更新用DTO
     * @param storeId 店舗ID（Cookieから取得）
     * @return 処理結果のレスポンス
     */
    @PutMapping("/{terminalId}")
    @ResponseBody
    public ResponseEntity<Map<String, String>> updateTerminal(@PathVariable Integer terminalId,
                                                              @RequestBody TerminalCreationDto dto,
                                                              @CookieValue("storeId") Integer storeId) {
        try {
            terminalService.updateTerminal(terminalId, dto, storeId);
            Map<String, String> response = new HashMap<>();
            response.put("message", "端末情報が正常に更新されました。");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, String> response = new HashMap<>();
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        } catch (Exception e) {
            Map<String, String> response = new HashMap<>();
            response.put("message", "端末の更新に失敗しました。");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 端末を削除します
     * 
     * @param terminalId 端末ID
     * @param storeId 店舗ID（Cookieから取得）
     * @return 処理結果のレスポンス
     */
    @DeleteMapping("/{terminalId}")
    @ResponseBody
    public ResponseEntity<Map<String, String>> deleteTerminal(@PathVariable Integer terminalId,
                                                              @CookieValue("storeId") Integer storeId) {
        try {
            terminalService.deleteTerminal(terminalId, storeId);
            Map<String, String> response = new HashMap<>();
            response.put("message", "端末が正常に削除されました。");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, String> response = new HashMap<>();
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        } catch (Exception e) {
            Map<String, String> response = new HashMap<>();
            response.put("message", "端末の削除に失敗しました。");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 店舗の座席一覧を取得します（端末設定用）
     * 
     * @param storeId 店舗ID（Cookieから取得）
     * @return 座席情報のリスト
     */
    @GetMapping("/seats")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getSeatsByStoreId(@CookieValue("storeId") Integer storeId) {
        try {
            List<Seat> seats = terminalService.getSeatsByStoreId(storeId);
            List<Map<String, Object>> seatList = seats.stream()
                .map(seat -> {
                    Map<String, Object> seatMap = new HashMap<>();
                    seatMap.put("seatId", seat.getSeatId());
                    seatMap.put("seatName", seat.getSeatName());
                    return seatMap;
                })
                .collect(Collectors.toList());
            return ResponseEntity.ok(seatList);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    /**
     * 店舗の端末一覧を取得します
     * 
     * @param storeId 店舗ID（Cookieから取得）
     * @return 端末情報のリスト
     */
    @GetMapping("/list")
    @ResponseBody
    public ResponseEntity<List<Terminal>> getTerminalsByStoreId(@CookieValue("storeId") Integer storeId) {
        if (storeId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        List<Terminal> terminals = terminalService.getTerminalsByStoreId(storeId);
        return ResponseEntity.ok(terminals);
    }
}