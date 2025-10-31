package com.order.service;

import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.order.entity.Menu;
import com.order.entity.MenuGroup;
import com.order.entity.Store;
import com.order.repository.MenuGroupRepository;
import com.order.repository.MenuRepository;
import com.order.repository.StoreRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MenuGroupService {

    private final MenuGroupRepository menuGroupRepository;
    private final StoreRepository storeRepository;
    private final MenuRepository menuRepository;

    // グループ一覧取得
    public List<MenuGroup> getMenuGroupsByStoreId(Integer storeId) {
//        Store store = storeRepository.findById(storeId)
//                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "店舗が見つかりません。"));
        return menuGroupRepository.findByStore_StoreIdOrderBySortOrderAsc(storeId);
    }

    // グループ追加
    @Transactional
    public MenuGroup addMenuGroup(Integer storeId, String groupName) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "店舗が見つかりません。"));

        if (menuGroupRepository.findByStoreAndGroupName(store, groupName).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "そのグループ名は既に存在します。");
        }

        MenuGroup newGroup = new MenuGroup();
        newGroup.setStore(store);
        newGroup.setGroupName(groupName);
        newGroup.setIsPlanTarget(false); // デフォルト値を設定
        newGroup.setForAdminOnly(false); // デフォルト値を設定

        // 新しいグループのsortOrderを設定
        Integer maxSortOrder = menuGroupRepository.findTopByStoreOrderBySortOrderDesc(store)
                                                     .orElse(0); // グループが一つもない場合は0
        newGroup.setSortOrder(maxSortOrder + 1);

        return menuGroupRepository.save(newGroup);
    }

    // グループ名編集
    @Transactional
    public MenuGroup updateGroupName(Integer groupId, String newGroupName, Integer storeId, boolean forAdminOnly) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "店舗が見つかりません。"));

        MenuGroup menuGroup = menuGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "メニューグループが見つかりません。"));

        // 該当の店舗に紐づくグループかチェック
        if (!menuGroup.getStore().getStoreId().equals(storeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不正な操作です。");
        }

        // グループ名重複チェック（自分自身を除く）
        Optional<MenuGroup> existingGroup = menuGroupRepository.findByStoreAndGroupName(store, newGroupName);
        if (existingGroup.isPresent() && !existingGroup.get().getGroupId().equals(groupId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "そのグループ名は既に存在します。");
        }
        menuGroup.setForAdminOnly(forAdminOnly);
        menuGroup.setGroupName(newGroupName);
        return menuGroupRepository.save(menuGroup);
    }

    // 並び順変更
    @Transactional
    public void reorderMenuGroup(Integer groupId, String direction, Integer storeId) {
//        Store store = storeRepository.findById(storeId)
//                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "店舗が見つかりません。"));

        MenuGroup targetGroup = menuGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "メニューグループが見つかりません。"));

        // 該当の店舗に紐づくグループかチェック
        if (!targetGroup.getStore().getStoreId().equals(storeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不正な操作です。");
        }

        List<MenuGroup> allGroupsInStore = menuGroupRepository.findByStore_StoreIdOrderBySortOrderAsc(storeId);
        int targetIndex = -1;
        for (int i = 0; i < allGroupsInStore.size(); i++) {
            if (allGroupsInStore.get(i).getGroupId().equals(targetGroup.getGroupId())) {
                targetIndex = i;
                break;
            }
        }

        if (targetIndex == -1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "対象のメニューグループが見つかりませんでした。");
        }

        if ("up".equalsIgnoreCase(direction)) {
            if (targetIndex > 0) {
                MenuGroup groupToSwap = allGroupsInStore.get(targetIndex - 1); // 上のグループ
                // sortOrderを入れ替える
                int tempOrder = targetGroup.getSortOrder();
                targetGroup.setSortOrder(groupToSwap.getSortOrder());
                groupToSwap.setSortOrder(tempOrder);
                menuGroupRepository.saveAll(List.of(targetGroup, groupToSwap));
            }
        } else if ("down".equalsIgnoreCase(direction)) {
            if (targetIndex < allGroupsInStore.size() - 1) {
                MenuGroup groupToSwap = allGroupsInStore.get(targetIndex + 1); // 下のグループ
                // sortOrderを入れ替える
                int tempOrder = targetGroup.getSortOrder();
                targetGroup.setSortOrder(groupToSwap.getSortOrder());
                groupToSwap.setSortOrder(tempOrder);
                menuGroupRepository.saveAll(List.of(targetGroup, groupToSwap));
            }
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "無効な方向です。'up'または'down'を指定してください。");
        }
    }
    
    // メニューグループの削除
    @Transactional
    public void deleteMenuGroup(Integer groupId, Integer storeId) {
        MenuGroup menuGroup = menuGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "メニューグループが見つかりません。"));
        
        // 該当の店舗に紐づくグループかチェック
        if (!menuGroup.getStore().getStoreId().equals(storeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不正な操作です。");
        }
        
        // 該当グループに属するメニューを取得し、menuGroupをnullに設定（未割当化）
        List<Menu> menusInGroup = menuRepository.findByMenuGroup_GroupIdAndDeletedAtIsNull(groupId);
        for (Menu menu : menusInGroup) {
            menu.setMenuGroup(null);
        }
        menuRepository.saveAll(menusInGroup);
        
        // メニューグループを削除
        menuGroupRepository.delete(menuGroup);
    }
}