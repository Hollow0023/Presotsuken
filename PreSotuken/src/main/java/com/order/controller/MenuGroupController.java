package com.order.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.order.entity.MenuGroup;
import com.order.entity.Store;
import com.order.repository.MenuGroupRepository;
import com.order.repository.StoreRepository;

@Controller
@RequestMapping("/menu/group")
public class MenuGroupController {

    @Autowired
    private MenuGroupRepository menuGroupRepository;

    @Autowired
    private StoreRepository storeRepository;

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

        Store store = storeRepository.findById(storeId).orElse(null);
        if (store == null) return Map.of("success", false, "error", "not_found");

        boolean exists = menuGroupRepository.findByStoreAndGroupName(store, groupName).isPresent();
        if (exists) return Map.of("success", false, "error", "duplicate");

        MenuGroup group = new MenuGroup();
        group.setStore(store);
        group.setGroupName(groupName);
        menuGroupRepository.save(group);

        return Map.of("success", true);
    }
} 
