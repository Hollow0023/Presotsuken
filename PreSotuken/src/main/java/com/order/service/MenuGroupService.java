package com.order.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.order.entity.MenuGroup;
import com.order.entity.Store;
import com.order.repository.MenuGroupRepository;
import com.order.repository.StoreRepository;

@Service
public class MenuGroupService {

    @Autowired
    private MenuGroupRepository menuGroupRepository;

    @Autowired
    private StoreRepository storeRepository;

    public boolean addMenuGroup(int storeId, String groupName) {
        Store store = storeRepository.findById(storeId).orElse(null);
        if (store == null) return false;

        boolean exists = menuGroupRepository.findByStoreAndGroupName(store, groupName).isPresent();
        if (exists) return false;

        MenuGroup newGroup = new MenuGroup();
        newGroup.setStore(store);
        newGroup.setGroupName(groupName);
        menuGroupRepository.save(newGroup);
        return true;
    }

    public List<MenuGroup> getGroupsByStore(int storeId) {
        Store store = storeRepository.findById(storeId).orElse(null);
        if (store == null) return List.of();
        return menuGroupRepository.findByStore(store);
    }
}
