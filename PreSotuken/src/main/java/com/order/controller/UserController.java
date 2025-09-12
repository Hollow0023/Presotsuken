package com.order.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.order.entity.User;
import com.order.service.UserService;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/admin/staff")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * スタッフ管理画面を表示
     */
    @GetMapping
    public String showStaffManagementPage(Model model) {
        return "staff_management";
    }

    /**
     * スタッフ一覧を取得（JSON API）
     */
    @GetMapping("/list")
    @ResponseBody
    public ResponseEntity<List<User>> getStaffList(@CookieValue("storeId") Integer storeId) {
        if (storeId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        
        List<User> users = userService.getUsersByStoreId(storeId);
        return ResponseEntity.ok(users);
    }

    /**
     * スタッフを追加
     */
    @PostMapping
    @ResponseBody
    public ResponseEntity<Map<String, String>> addStaff(
            @RequestParam("userName") String userName,
            @RequestParam(value = "isAdmin", required = false) Boolean isAdmin,
            @CookieValue("storeId") Integer storeId) {
        
        Map<String, String> response = new HashMap<>();
        
        if (storeId == null) {
            response.put("message", "店舗IDが指定されていません。");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
        
        try {
            userService.createUser(userName, isAdmin, storeId);
            response.put("message", "スタッフが正常に追加されました。");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        } catch (Exception e) {
            System.err.println("スタッフ追加中にエラーが発生しました: " + e.getMessage());
            response.put("message", "スタッフの追加に失敗しました。");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * スタッフを更新
     */
    @PutMapping("/{userId}")
    @ResponseBody
    public ResponseEntity<Map<String, String>> updateStaff(
            @PathVariable Integer userId,
            @RequestParam("userName") String userName,
            @RequestParam(value = "isAdmin", required = false) Boolean isAdmin,
            @CookieValue("storeId") Integer storeId) {
        
        Map<String, String> response = new HashMap<>();
        
        if (storeId == null) {
            response.put("message", "店舗IDが指定されていません。");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
        
        try {
            userService.updateUser(userId, userName, isAdmin, storeId);
            response.put("message", "スタッフ情報が正常に更新されました。");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        } catch (Exception e) {
            System.err.println("スタッフ更新中にエラーが発生しました: " + e.getMessage());
            response.put("message", "スタッフの更新に失敗しました。");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * スタッフを削除
     */
    @DeleteMapping("/{userId}")
    @ResponseBody
    public ResponseEntity<Map<String, String>> deleteStaff(
            @PathVariable Integer userId,
            @CookieValue("storeId") Integer storeId) {
        
        Map<String, String> response = new HashMap<>();
        
        if (storeId == null) {
            response.put("message", "店舗IDが指定されていません。");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
        
        try {
            userService.deleteUser(userId, storeId);
            response.put("message", "スタッフが正常に削除されました。");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        } catch (Exception e) {
            System.err.println("スタッフ削除中にエラーが発生しました: " + e.getMessage());
            response.put("message", "スタッフの削除に失敗しました。");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}