package com.order.controller;

import java.util.List;

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

import com.order.dto.MenuTimeSlotRequest;
import com.order.entity.MenuTimeSlot;
import com.order.service.MenuTimeSlotService;

import lombok.RequiredArgsConstructor;

/**
 * メニュー時間帯の管理を行うコントローラ
 * 時間帯の一覧表示、追加、更新、削除を担当します
 */
@Controller
@RequestMapping("/admin/time-slots")
@RequiredArgsConstructor
public class MenuTimeSlotController {

    private final MenuTimeSlotService menuTimeSlotService;

    /**
     * 時間帯管理ページを表示します
     * 
     * @param storeId 店舗ID（Cookieから取得）
     * @param model ビューに渡すモデル
     * @return 時間帯管理画面のテンプレート名
     */
    @GetMapping
    public String showTimeSlotPage(@CookieValue("storeId") int storeId, Model model) {
        model.addAttribute("storeId", storeId);
        model.addAttribute("timeSlots", menuTimeSlotService.getTimeSlotsByStoreId(storeId));
        return "time_slot_management";
    }

    /**
     * 指定した店舗の時間帯一覧を取得します
     * 
     * @param storeId 店舗ID
     * @return 時間帯のリスト
     */
    @GetMapping("/by-store/{storeId}")
    @ResponseBody
    public List<MenuTimeSlot> getTimeSlots(@PathVariable int storeId) {
        return menuTimeSlotService.getTimeSlotsByStoreId(storeId);
    }

    /**
     * 新規時間帯を作成します
     * 
     * @param request 時間帯リクエスト
     * @return 作成した時間帯
     */
    @PostMapping
    @ResponseBody
    public ResponseEntity<MenuTimeSlot> createTimeSlot(@RequestBody MenuTimeSlotRequest request) {
        MenuTimeSlot timeSlot = new MenuTimeSlot();
        timeSlot.setName(request.getName());
        timeSlot.setStartTime(request.getStartTime());
        timeSlot.setEndTime(request.getEndTime());
        
        MenuTimeSlot created = menuTimeSlotService.createTimeSlot(timeSlot, request.getStoreId());
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    /**
     * 時間帯を更新します
     * 
     * @param timeSlotId 時間帯ID
     * @param request 更新する時間帯リクエスト
     * @return 更新した時間帯
     */
    @PutMapping("/{timeSlotId}")
    @ResponseBody
    public ResponseEntity<MenuTimeSlot> updateTimeSlot(@PathVariable int timeSlotId,
                                                       @RequestBody MenuTimeSlotRequest request) {
        if (timeSlotId != request.getTimeSlotId()) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        try {
            MenuTimeSlot timeSlot = new MenuTimeSlot();
            timeSlot.setTimeSlotId(request.getTimeSlotId());
            timeSlot.setName(request.getName());
            timeSlot.setStartTime(request.getStartTime());
            timeSlot.setEndTime(request.getEndTime());
            
            MenuTimeSlot updated = menuTimeSlotService.updateTimeSlot(timeSlot);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    /**
     * 時間帯を削除します
     * 
     * @param timeSlotId 時間帯ID
     * @return 削除結果
     */
    @DeleteMapping("/{timeSlotId}")
    @ResponseBody
    public ResponseEntity<Void> deleteTimeSlot(@PathVariable int timeSlotId) {
        try {
            menuTimeSlotService.deleteTimeSlot(timeSlotId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }
}
