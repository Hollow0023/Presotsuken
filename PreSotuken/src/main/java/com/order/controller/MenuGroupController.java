package com.order.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import com.order.entity.MenuGroup;
import com.order.entity.Store;
import com.order.repository.MenuGroupRepository;
import com.order.repository.StoreRepository;
import com.order.service.MenuGroupService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/menu/group")
public class MenuGroupController {

    private final MenuGroupRepository menuGroupRepository;
    private final StoreRepository storeRepository;
    private final MenuGroupService menuGroupService;

 // HTMLページ表示用
    @GetMapping("/add")
    public String showAddPage(@CookieValue("storeId") int storeId, Model model) {
        Store store = storeRepository.findById(storeId).orElse(null);
        if (store == null) return "error/404";
        model.addAttribute("storeId", storeId);
        return "menu_group_add"; // HTMLページ名
    }

    // JSON返却用API
    @PostMapping("/api/add")
    @ResponseBody
    public Map<String, Object> addGroup(@RequestBody Map<String, String> body) {
        int storeId = Integer.parseInt(body.get("storeId"));
        String groupName = body.get("groupName");
        boolean forAdminOnly = Boolean.parseBoolean(body.getOrDefault("forAdminOnly", "false")); 


        Store store = storeRepository.findById(storeId).orElse(null);
        if (store == null) return Map.of("success", false, "error", "not_found");

        boolean exists = menuGroupRepository.findByStoreAndGroupName(store, groupName).isPresent();
        if (exists) return Map.of("success", false, "error", "duplicate");

        MenuGroup group = new MenuGroup();
        group.setStore(store);
        group.setGroupName(groupName);
        group.setForAdminOnly(forAdminOnly); // forAdminOnlyをセット
        menuGroupRepository.save(group);

        return Map.of("success", true);
    }
    
 // NEW: JSON返却用API (グループ一覧取得)
    @GetMapping("/api/list/{storeId}")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getGroupsByStore(@PathVariable Integer storeId) {
        try {
            List<MenuGroup> menuGroups = menuGroupService.getMenuGroupsByStoreId(storeId);
            List<Map<String, Object>> responseList = menuGroups.stream()
            	    .map(group -> {
            	        Map<String, Object> groupMap = new java.util.HashMap<>(); // ★ HashMapをインスタンス化
            	        groupMap.put("groupId", group.getGroupId());
            	        groupMap.put("groupName", group.getGroupName());
            	        groupMap.put("sortOrder", group.getSortOrder());
            	        groupMap.put("isPlanTarget", group.getIsPlanTarget() != null ? group.getIsPlanTarget() : false);
            	        groupMap.put("forAdminOnly", group.getForAdminOnly() != null ? group.getForAdminOnly() : false);
            	        return groupMap;
            	    })
            	    .collect(Collectors.toList());
            return ResponseEntity.ok(responseList);
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    
    // NEW: JSON返却用API (グループ名編集)
    @PutMapping("/api/edit/{groupId}") // PathVariable名もgroupIdに合わせる
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateGroup(@PathVariable Integer groupId, @RequestBody Map<String, String> body) {
        try {
            String newGroupName = body.get("groupName");
            int storeId = Integer.parseInt(body.get("storeId"));
            boolean newForAdminOnly = Boolean.parseBoolean(body.getOrDefault("forAdminOnly", "false")); 


            menuGroupService.updateGroupName(groupId, newGroupName, storeId, newForAdminOnly); 
            return ResponseEntity.ok(Map.of("success", true));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "invalid_input"));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("success", false, "error", e.getReason()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("success", false, "error", "unknown_error"));
        }
    }

    // NEW: JSON返却用API (並び順変更)
    @PutMapping("/api/reorder")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> reorderGroup(@RequestBody Map<String, Object> body) {
        try {
            Integer groupId = (Integer) body.get("groupId"); // リクエストボディのキーもgroupId
            String direction = (String) body.get("direction");
            Integer storeId = (Integer) body.get("storeId");

            if (groupId == null || direction == null || storeId == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "error", "invalid_input"));
            }

            menuGroupService.reorderMenuGroup(groupId, direction, storeId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("success", false, "error", e.getReason()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("success", false, "error", "unknown_error"));
        }
    }

} 
