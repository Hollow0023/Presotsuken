package com.order.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.order.dto.OptionDeletionCheckDTO;
import com.order.entity.MenuOption;
import com.order.entity.OptionGroup;
import com.order.entity.OptionItem;
import com.order.repository.MenuOptionRepository;
import com.order.repository.OptionGroupRepository;
import com.order.repository.OptionItemRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor // finalフィールドのコンストラクタを自動生成
public class OptionManagementService {

    private final OptionGroupRepository optionGroupRepository;
    private final OptionItemRepository optionItemRepository;
    private final MenuOptionRepository menuOptionRepository;

    // オプショングループ関連のメソッド

    @Transactional
    public OptionGroup createOptionGroup(OptionGroup optionGroup) {
        return optionGroupRepository.save(optionGroup);
    }

    public Optional<OptionGroup> getOptionGroupById(int id) {
        return optionGroupRepository.findById(id);
    }

    public List<OptionGroup> getAllOptionGroups(int storeId) {
        return optionGroupRepository.findByStoreId(storeId);
    }

    @Transactional
    public OptionGroup updateOptionGroup(OptionGroup updatedOptionGroup) {
        // 更新対象が存在するか確認
        return optionGroupRepository.findById(updatedOptionGroup.getOptionGroupId())
                .map(existingGroup -> {
                    existingGroup.setGroupName(updatedOptionGroup.getGroupName());
                    existingGroup.setStoreId(updatedOptionGroup.getStoreId()); // 必要であればstoreIdも更新
                    return optionGroupRepository.save(existingGroup);
                })
                .orElseThrow(() -> new IllegalArgumentException("OptionGroup with ID " + updatedOptionGroup.getOptionGroupId() + " not found."));
    }

    @Transactional
    public void deleteOptionGroup(int id) {
        // オプショングループを削除する前に、関連するオプションアイテム・メニューオプションも削除する
        List<OptionItem> items = optionItemRepository.findByOptionGroupId(id);
        optionItemRepository.deleteAll(items); // 関連アイテムを削除
        
        // 関連するMenuOptionも削除
        List<MenuOption> menuOptions = menuOptionRepository.findByOptionGroupId(id);
        menuOptionRepository.deleteAll(menuOptions);
        
        optionGroupRepository.deleteById(id);
    }

    /**
     * オプショングループの削除前チェック：関連するメニューがあるかを確認
     */
    public OptionDeletionCheckDTO checkOptionGroupDeletion(int optionGroupId) {
        List<MenuOption> linkedMenuOptions = menuOptionRepository.findByOptionGroupIdWithMenu(optionGroupId);
        
        if (linkedMenuOptions.isEmpty()) {
            return new OptionDeletionCheckDTO(false, List.of());
        }
        
        List<OptionDeletionCheckDTO.LinkedMenuInfo> linkedMenus = linkedMenuOptions.stream()
            .map(menuOption -> new OptionDeletionCheckDTO.LinkedMenuInfo(
                menuOption.getMenu().getMenuId(),
                menuOption.getMenu().getMenuName()
            ))
            .distinct() // 重複排除
            .collect(Collectors.toList());
            
        return new OptionDeletionCheckDTO(true, linkedMenus);
    }
    
    /**
     * オプショングループと関連するメニューオプションを全て削除
     */
    @Transactional
    public void deleteOptionGroupWithMenuOptions(int optionGroupId) {
        // 関連するMenuOptionを削除
        List<MenuOption> menuOptions = menuOptionRepository.findByOptionGroupId(optionGroupId);
        menuOptionRepository.deleteAll(menuOptions);
        
        // 通常のオプショングループ削除処理を実行
        deleteOptionGroup(optionGroupId);
    }

    // オプションアイテム関連のメソッド

    @Transactional
    public OptionItem createOptionItem(OptionItem optionItem) {
        // 関連するOptionGroupが存在するか確認することも可能だが、ここではシンプルに保存
        return optionItemRepository.save(optionItem);
    }

    public Optional<OptionItem> getOptionItemById(int id) {
        return optionItemRepository.findById(id);
    }

    public List<OptionItem> getOptionItemsByGroupId(int optionGroupId) {
        return optionItemRepository.findByOptionGroupId(optionGroupId);
    }

    @Transactional
    public OptionItem updateOptionItem(OptionItem updatedOptionItem) {
        // 更新対象が存在するか確認
        return optionItemRepository.findById(updatedOptionItem.getOptionItemId())
                .map(existingItem -> {
                    existingItem.setItemName(updatedOptionItem.getItemName());
                    // 必要であれば optionGroupId も更新 (グループ変更)
                    existingItem.setOptionGroupId(updatedOptionItem.getOptionGroupId());
                    // 価格などがあれば追加
                    // existingItem.setPrice(updatedOptionItem.getPrice());
                    return optionItemRepository.save(existingItem);
                })
                .orElseThrow(() -> new IllegalArgumentException("OptionItem with ID " + updatedOptionItem.getOptionItemId() + " not found."));
    }

    @Transactional
    public void deleteOptionItem(int id) {
        optionItemRepository.deleteById(id);
    }
}