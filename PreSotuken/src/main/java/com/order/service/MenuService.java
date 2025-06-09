package com.order.service;

import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.order.dto.MenuWithOptionsDTO;
import com.order.dto.OptionGroupDTO;
import com.order.dto.OptionItemDTO;
import com.order.entity.Menu;
import com.order.entity.MenuTimeSlot;
import com.order.repository.MenuOptionRepository;
import com.order.repository.MenuRepository;
import com.order.repository.MenuTimeSlotRepository;
import com.order.repository.OptionGroupRepository;
import com.order.repository.OptionItemRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MenuService {

    private final MenuTimeSlotRepository menuTimeSlotRepository;

    @Autowired
    private MenuRepository menuRepository;
    @Autowired
    private MenuOptionRepository menuOptionRepository;
    @Autowired
    private OptionGroupRepository optionGroupRepository;
    @Autowired
    private OptionItemRepository optionItemRepository;

    // 時間帯を絞って表示
    public List<MenuWithOptionsDTO> getMenusWithOptions(Integer storeId) {
        LocalTime now = LocalTime.now();

        List<MenuTimeSlot> timeSlots = menuTimeSlotRepository.findByStoreStoreId(storeId);
        MenuTimeSlot currentSlot = timeSlots.stream()
                .filter(slot -> !now.isBefore(slot.getStartTime()) && now.isBefore(slot.getEndTime()))
                .findFirst()
                .orElse(null);

        if (currentSlot == null) {
            return List.of();
        }

        List<Menu> menus = menuRepository.findMenusAvailableAt(now).stream()
                .filter(menu -> menu.getStore().getStoreId().equals(storeId))
                .filter(menu -> !Boolean.TRUE.equals(menu.getIsSoldOut()))
                .collect(Collectors.toList());

        return menus.stream().map(this::toDto).collect(Collectors.toList());
    }

    // 全てのメニューを表示
    public List<MenuWithOptionsDTO> getAllMenusWithOptions(Integer storeId) {
        List<Menu> menus = menuRepository.findByStore_StoreId(storeId);
        return menus.stream().map(this::toDto).collect(Collectors.toList());
    }

    // 共通：メニュー → DTO（オプション、税込価格含む）
    public MenuWithOptionsDTO toDto(Menu menu) {
        MenuWithOptionsDTO dto = new MenuWithOptionsDTO();
        dto.setMenuId(menu.getMenuId());
        dto.setMenuName(menu.getMenuName());
        dto.setPrice(menu.getPrice());
        dto.setMenuImage(menu.getMenuImage());
        dto.setDescription(menu.getMenuDescription());
        dto.setTaxRate(menu.getTaxRate());
        dto.setMenuGroup(menu.getMenuGroup());
        dto.setIsSoldOut(menu.getIsSoldOut());

        // 税込み価格の計算（税率がnullの場合は0%）
        double rate = menu.getTaxRate() != null ? menu.getTaxRate().getRate() : 0.0;
        int priceWithTax = (int) Math.round(menu.getPrice() * (1 + rate));
        dto.setPriceWithTax(priceWithTax);

        // オプショングループの構築
        var menuOptions = menuOptionRepository.findByMenu_MenuId(menu.getMenuId());
        List<OptionGroupDTO> groupDTOs = menuOptions.stream().map(menuOption -> {
            var group = optionGroupRepository.findById(menuOption.getOptionGroupId()).orElse(null);
            if (group == null) return null;

            var groupDTO = new OptionGroupDTO();
            groupDTO.setOptionGroupId(group.getOptionGroupId());
            groupDTO.setGroupName(group.getGroupName());

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
        }).filter(group -> group != null).collect(Collectors.toList());

        dto.setOptionGroups(groupDTOs);

        return dto;
    }
    /**
     * 指定されたIDのメニューの品切れ状態を更新する
     * @param menuId 更新対象のメニューID
     * @param isSoldOut 品切れ状態 (true:品切れ中, false:品切れ解除)
     * @return 更新されたメニュー、または見つからない場合はnull
     */
    public Menu updateMenuSoldOutStatus(Integer menuId, Boolean isSoldOut) {
        // menuRepositoryは@Autowiredで注入済みなのでそのまま使える
        return menuRepository.findById(menuId).map(menu -> {
            menu.setIsSoldOut(isSoldOut);
            return menuRepository.save(menu);
        }).orElse(null); // Optional.map() と orElse() を使うとスッキリ書けるよ
    }
 // MenuService.java (既存のMenuServiceに追記)

    /**
     * 指定された複数のメニューIDの品切れ状態を一括で更新する
     * @param menuIds 更新対象のメニューIDのリスト
     * @param isSoldOut 品切れ状態 (true:品切れ中, false:品切れ解除)
     * @return 更新されたメニューのリスト
     */
    public List<Menu> updateMultipleMenuSoldOutStatus(List<Integer> menuIds, Boolean isSoldOut) {
        List<Menu> menusToUpdate = menuRepository.findAllById(menuIds);

        for (Menu menu : menusToUpdate) {
            menu.setIsSoldOut(isSoldOut);
        }
        
        return menuRepository.saveAll(menusToUpdate);
    }
}
