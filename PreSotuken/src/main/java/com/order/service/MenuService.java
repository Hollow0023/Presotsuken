package com.order.service;

import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.order.dto.MenuWithOptionsDTO;
import com.order.dto.OptionGroupDTO;
import com.order.dto.OptionItemDTO;
import com.order.entity.Menu;
import com.order.entity.MenuTimeSlot;
import com.order.entity.Plan;
import com.order.repository.MenuOptionRepository;
import com.order.repository.MenuRepository;
import com.order.repository.MenuTimeSlotRepository;
import com.order.repository.OptionGroupRepository;
import com.order.repository.OptionItemRepository;
import com.order.repository.PlanRepository;

import lombok.RequiredArgsConstructor;

/**
 * メニュー管理に関するビジネスロジックを提供するサービス
 * メニューの取得、時間帯フィルタリング、DTOへの変換などを担当します
 */
@Service
@RequiredArgsConstructor
public class MenuService {

    private final MenuTimeSlotRepository menuTimeSlotRepository;
    private final MenuRepository menuRepository;
    private final MenuOptionRepository menuOptionRepository;
    private final OptionGroupRepository optionGroupRepository;
    private final OptionItemRepository optionItemRepository;
    private final PlanRepository planRepository;

    /**
     * 現在の時間帯に合致し、品切れでないメニューとオプションを取得します
     * 
     * @param storeId 店舗ID
     * @return メニューとオプション情報のDTOリスト
     */
    public List<MenuWithOptionsDTO> getMenusWithOptions(Integer storeId) {
        LocalTime now = LocalTime.now();
        
        List<MenuTimeSlot> allTimeSlots = menuTimeSlotRepository.findByStoreStoreId(storeId);
        
        // 現在時刻に合致する全ての時間帯スロットを取得
        List<Integer> currentSlotIds = allTimeSlots.stream()
                .filter(slot -> !now.isBefore(slot.getStartTime()) && now.isBefore(slot.getEndTime()))
                .map(MenuTimeSlot::getTimeSlotId)
                .collect(Collectors.toList());

        if (currentSlotIds.isEmpty()) {
            return List.of();
        }

        // 取得した複数のtimeSlotIdに紐づくメニューを全て取得し、重複を除去してソート（削除されていないもののみ）
        List<Menu> menus = menuRepository.findByStore_StoreIdAndIsSoldOutFalseAndTimeSlot_TimeSlotIdInAndDeletedAtIsNullOrderByMenuNameAsc(storeId, currentSlotIds);

        return menus.stream().map(this::toDto).collect(Collectors.toList());
    }
    
    /**
     * 指定店舗の全プランを取得します
     * 
     * @param storeId 店舗ID
     * @return プランのリスト
     */
    public List<Plan> getAllPlans(Integer storeId) {
        return planRepository.findByStore_StoreId(storeId);
    }

    /**
     * 品切れ状態に関係なく全てのメニューとオプションを取得します（管理者用、削除されていないもののみ）
     * 
     * @param storeId 店舗ID
     * @return 全メニューとオプション情報のDTOリスト
     */
    public List<MenuWithOptionsDTO> getAllMenusWithOptions(Integer storeId) {
        List<Menu> menus = menuRepository.findByStore_StoreIdAndDeletedAtIsNullOrderByMenuIdAsc(storeId);
        return menus.stream().map(this::toDto).collect(Collectors.toList());
    }

    /**
     * MenuエンティティをMenuWithOptionsDTOに変換します
     * オプション情報、税込価格、メニューグループ情報などを含めて変換します
     * 
     * @param menu 変換対象のMenuエンティティ
     * @return 変換されたDTO
     */
    public MenuWithOptionsDTO toDto(Menu menu) {
        MenuWithOptionsDTO dto = new MenuWithOptionsDTO();

        // 基本情報のマッピング
        dto.setMenuId(menu.getMenuId());
        dto.setMenuName(menu.getMenuName());
        dto.setPrice(menu.getPrice());
        dto.setMenuImage(menu.getMenuImage());
        dto.setDescription(menu.getMenuDescription());
        dto.setIsSoldOut(menu.getIsSoldOut());

        // 税率関連のマッピング
        mapTaxRateInfo(menu, dto);

        // メニューグループ関連のマッピング
        mapMenuGroupInfo(menu, dto);

        // 飲み放題関連のフィールドをDTOにセット
        dto.setIsPlanStarter(menu.getIsPlanStarter());
        dto.setPlanId(menu.getPlanId());
        
        // オプショングループの構築
        buildOptionGroups(menu, dto);

        return dto;
    }

    /**
     * 指定されたIDのメニューの品切れ状態を更新します（削除されていないメニューのみ）
     * 
     * @param menuId 更新対象のメニューID
     * @param isSoldOut 品切れ状態 (true:品切れ中, false:品切れ解除)
     * @return 更新されたメニュー、または見つからない/削除済みの場合はnull
     */
    public Menu updateMenuSoldOutStatus(Integer menuId, Boolean isSoldOut) {
        return menuRepository.findById(menuId).map(menu -> {
            // 削除済みメニューは更新不可
            if (menu.getDeletedAt() != null) {
                return null;
            }
            menu.setIsSoldOut(isSoldOut);
            return menuRepository.save(menu);
        }).orElse(null);
    }

    /**
     * 指定された複数のメニューIDの品切れ状態を一括で更新します（削除されていないメニューのみ）
     * 
     * @param menuIds 更新対象のメニューIDのリスト
     * @param isSoldOut 品切れ状態 (true:品切れ中, false:品切れ解除)
     * @return 更新されたメニューのリスト（削除済みメニューは除外）
     */
    public List<Menu> updateMultipleMenuSoldOutStatus(List<Integer> menuIds, Boolean isSoldOut) {
        List<Menu> menusToUpdate = menuRepository.findAllById(menuIds);

        // 削除されていないメニューのみを更新対象とする
        List<Menu> validMenus = menusToUpdate.stream()
                .filter(menu -> menu.getDeletedAt() == null)
                .collect(Collectors.toList());

        for (Menu menu : validMenus) {
            menu.setIsSoldOut(isSoldOut);
        }
        
        return menuRepository.saveAll(validMenus);
    }

    /**
     * 税率情報をDTOにマッピングします
     */
    private void mapTaxRateInfo(Menu menu, MenuWithOptionsDTO dto) {
        if (menu.getTaxRate() != null) {
            dto.setTaxRateId(menu.getTaxRate().getTaxRateId());
            dto.setTaxRateValue(menu.getTaxRate().getRate());
            double rate = menu.getTaxRate().getRate();
            dto.setPriceWithTax((double) Math.round(menu.getPrice() * (1 + rate))); 
        } else {
            dto.setTaxRateId(null);
            dto.setTaxRateValue(0.0);
            dto.setPriceWithTax(menu.getPrice());
        }
    }

    /**
     * メニューグループ情報をDTOにマッピングします
     */
    private void mapMenuGroupInfo(Menu menu, MenuWithOptionsDTO dto) {
        if (menu.getMenuGroup() != null) {
            dto.setMenuGroupId(menu.getMenuGroup().getGroupId());
            dto.setMenuGroupName(menu.getMenuGroup().getGroupName());
            dto.setMenuGroupIsPlanTarget(menu.getMenuGroup().getIsPlanTarget());
            dto.setMenuGroupSortOrder(menu.getMenuGroup().getSortOrder());
        } else {
            dto.setMenuGroupId(null);
            dto.setMenuGroupName(null);
            dto.setMenuGroupIsPlanTarget(false);
            dto.setMenuGroupSortOrder(Integer.MAX_VALUE);
        }
    }

    /**
     * オプショングループ情報を構築してDTOに設定します
     */
    private void buildOptionGroups(Menu menu, MenuWithOptionsDTO dto) {
        var menuOptions = menuOptionRepository.findByMenu_MenuId(menu.getMenuId());
        List<OptionGroupDTO> groupDTOs = menuOptions.stream()
            .map(menuOption -> {
                var group = optionGroupRepository.findById(menuOption.getOptionGroupId()).orElse(null);
                if (group == null) return null;

                var groupDTO = new OptionGroupDTO();
                groupDTO.setOptionGroupId(group.getOptionGroupId());
                groupDTO.setGroupName(group.getGroupName());

                // OptionItemのリストをOptionItemDTOに変換
                var itemDTOs = optionItemRepository.findByOptionGroupId(group.getOptionGroupId())
                        .stream()
                        .map(item -> {
                            var itemDTO = new OptionItemDTO();
                            itemDTO.setOptionItemId(item.getOptionItemId());
                            itemDTO.setItemName(item.getItemName());
                            return itemDTO;
                        }).collect(Collectors.toList());

                groupDTO.setOptionItems(itemDTOs);
                return groupDTO;
            })
            .filter(group -> group != null)
            .collect(Collectors.toList());

        dto.setOptionGroups(groupDTOs);
    }
}