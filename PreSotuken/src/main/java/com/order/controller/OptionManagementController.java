package com.order.controller;

import java.util.List;
import java.util.Map;
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

import com.order.dto.OptionDeletionCheckDTO;
import com.order.entity.OptionGroup;
import com.order.entity.OptionItem;
import com.order.service.OptionManagementService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/options") // 管理画面のURLパスを設定
public class OptionManagementController {

    private final OptionManagementService optionManagementService;

    // --- 管理画面のトップページ表示 ---
    @GetMapping
    public String showOptionManagementPage(@CookieValue("storeId") int storeId, Model model) {
        // 全てのオプショングループとそれに紐づくアイテムを取得して表示
        List<OptionGroup> optionGroups = optionManagementService.getAllOptionGroups(storeId);
        
        // オプショングループごとにアイテムをMapにまとめる（Thymeleafで表示しやすいように）
        Map<Integer, List<OptionItem>> itemsByGroup = optionGroups.stream()
                .collect(Collectors.toMap(
                    OptionGroup::getOptionGroupId,
                    group -> optionManagementService.getOptionItemsByGroupId(group.getOptionGroupId())
                ));
        model.addAttribute("optionGroups", optionGroups);
        model.addAttribute("itemsByGroup", itemsByGroup);
        model.addAttribute("storeId", storeId); // 新規作成時に必要になるかも

        return "option_management"; // Thymeleafテンプレート名
    }

    // --- オプショングループのCRUD操作 ---

    // 新規オプショングループの作成 (POSTリクエストを受け付けるAPI)
    @PostMapping("/groups")
    @ResponseBody // JSONレスポンスを返す
    public ResponseEntity<OptionGroup> createOptionGroup(@RequestBody OptionGroup optionGroup) {
        OptionGroup createdGroup = optionManagementService.createOptionGroup(optionGroup);
        return new ResponseEntity<>(createdGroup, HttpStatus.CREATED);
    }

    // 既存オプショングループの更新 (PUTリクエストを受け付けるAPI)
    @PutMapping("/groups/{groupId}")
    @ResponseBody // JSONレスポンスを返す
    public ResponseEntity<OptionGroup> updateOptionGroup(@PathVariable int groupId, @RequestBody OptionGroup optionGroup) {
        if (groupId != optionGroup.getOptionGroupId()) {
            // パス変数とリクエストボディのIDが一致しない場合はエラー
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        try {
            OptionGroup updatedGroup = optionManagementService.updateOptionGroup(optionGroup);
            return ResponseEntity.ok(updatedGroup);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND); // 存在しないグループの場合
        }
    }

    // オプショングループの削除 (DELETEリクエストを受け付けるAPI)
    @DeleteMapping("/groups/{groupId}")
    @ResponseBody // ステータスコードのみ返す
    public ResponseEntity<Void> deleteOptionGroup(@PathVariable int groupId) {
        try {
            optionManagementService.deleteOptionGroupWithMenuOptions(groupId);
            return ResponseEntity.noContent().build(); // 成功（コンテンツなし）
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND); // 存在しないグループの場合
        }
    }
    
    // オプショングループ削除前のメニュー関連チェック
    @GetMapping("/groups/{groupId}/deletion-check")
    @ResponseBody
    public ResponseEntity<OptionDeletionCheckDTO> checkOptionGroupDeletion(@PathVariable int groupId) {
        try {
            OptionDeletionCheckDTO checkResult = optionManagementService.checkOptionGroupDeletion(groupId);
            return ResponseEntity.ok(checkResult);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // --- オプションアイテムのCRUD操作 ---

    // 新規オプションアイテムの作成 (POSTリクエストを受け付けるAPI)
    @PostMapping("/items")
    @ResponseBody // JSONレスポンスを返す
    public ResponseEntity<OptionItem> createOptionItem(@RequestBody OptionItem optionItem) {
        OptionItem createdItem = optionManagementService.createOptionItem(optionItem);
        return new ResponseEntity<>(createdItem, HttpStatus.CREATED);
    }

    // 既存オプションアイテムの更新 (PUTリクエストを受け付けるAPI)
    @PutMapping("/items/{itemId}")
    @ResponseBody // JSONレスポンスを返す
    public ResponseEntity<OptionItem> updateOptionItem(@PathVariable int itemId, @RequestBody OptionItem optionItem) {
        if (itemId != optionItem.getOptionItemId()) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        try {
            OptionItem updatedItem = optionManagementService.updateOptionItem(optionItem);
            return ResponseEntity.ok(updatedItem);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    // オプションアイテムの削除 (DELETEリクエストを受け付けるAPI)
    @DeleteMapping("/items/{itemId}")
    @ResponseBody // ステータスコードのみ返す
    public ResponseEntity<Void> deleteOptionItem(@PathVariable int itemId) {
        try {
            optionManagementService.deleteOptionItem(itemId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }
}