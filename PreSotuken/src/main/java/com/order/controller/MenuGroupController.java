package com.order.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.order.entity.MenuGroup;
import com.order.service.MenuGroupService;

@RestController
@RequestMapping("/menu/group")
public class MenuGroupController {

    @Autowired
    private MenuGroupService menuGroupService;

    @PostMapping("/add")
    public Map<String, Object> addGroup(@RequestBody Map<String, String> body) {
        int storeId = Integer.parseInt(body.get("storeId"));
        String groupName = body.get("groupName");

        boolean success = menuGroupService.addMenuGroup(storeId, groupName);
        return Map.of("success", success);
    }

    @GetMapping("/list")
    public List<Map<String, Object>> getGroups(@RequestParam int storeId) {
        List<MenuGroup> groups = menuGroupService.getGroupsByStore(storeId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (MenuGroup group : groups) {
            result.add(Map.of(
                "groupId", group.getGroupId(),
                "groupName", group.getGroupName()
            ));
        }
        return result;
    }
}
