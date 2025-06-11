package com.order.service;

import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

// ★ @Autowired は削除し、コンストラクタインジェクションに統一するため
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

@Service
@RequiredArgsConstructor // finalフィールドのコンストラクタを自動生成
public class MenuService {

    private final MenuTimeSlotRepository menuTimeSlotRepository;
    private final MenuRepository menuRepository;
    private final MenuOptionRepository menuOptionRepository;
    private final OptionGroupRepository optionGroupRepository;
    private final OptionItemRepository optionItemRepository;
    private final PlanRepository planRepository;


    // 時間帯を絞って表示 (品切れは表示しない)
    public List<MenuWithOptionsDTO> getMenusWithOptions(Integer storeId) {
        LocalTime now = LocalTime.now();

        List<MenuTimeSlot> allTimeSlots = menuTimeSlotRepository.findByStoreStoreId(storeId);
        
        // 現在時刻に合致する全ての時間帯スロットを取得
        List<Integer> currentSlotIds = allTimeSlots.stream()
                .filter(slot -> !now.isBefore(slot.getStartTime()) && now.isBefore(slot.getEndTime()))
                .map(MenuTimeSlot::getTimeSlotId) // 合致するスロットのIDだけを抽出
                .collect(Collectors.toList());

        if (currentSlotIds.isEmpty()) {
            return List.of(); // 該当する時間帯スロットが一つもなければ空リストを返す
        }

        // 取得した複数のtimeSlotIdに紐づくメニューを全て取得し、重複を除去してソート
        // MenuRepositoryに新しいクエリメソッドが必要になる
        List<Menu> menus = menuRepository.findByStore_StoreIdAndIsSoldOutFalseAndTimeSlot_TimeSlotIdInOrderByMenuNameAsc(storeId, currentSlotIds);
        // findByStore_StoreIdAndIsSoldOutFalseAndMenuTimeSlotTimeSlotIdInOrderByMenuNameAsc が無い場合、
        // Streamでフィルタリングしてから toDto する
        // List<Menu> menus = menuRepository.findByStore_StoreIdAndIsSoldOutFalseOrderByMenuNameAsc(storeId).stream()
        //      .filter(menu -> menu.getTimeSlot() != null && currentSlotIds.contains(menu.getTimeSlot().getTimeSlotId()))
        //      .collect(Collectors.toList());


        return menus.stream().map(this::toDto).collect(Collectors.toList());
    }
    
    public List<Plan> getAllPlans(Integer storeId) {
        // PlanエンティティがStoreエンティティを関連フィールド'store'で持っている場合
        return planRepository.findByStore_StoreId(storeId);
        // もしPlanが直接storeIdを持つなら、return planRepository.findByStoreId(storeId);
    }

    // 全てのメニューを表示 (品切れも表示)
    public List<MenuWithOptionsDTO> getAllMenusWithOptions(Integer storeId) {
        // ★修正: storeId でフィルタリングし、menu_name でソート
        List<Menu> menus = menuRepository.findByStore_StoreIdOrderByMenuIdAsc(storeId);
        return menus.stream().map(this::toDto).collect(Collectors.toList());
    }

    // 共通：メニュー → DTO（オプション、税込価格含む）
    public MenuWithOptionsDTO toDto(Menu menu) {
        MenuWithOptionsDTO dto = new MenuWithOptionsDTO();

        dto.setMenuId(menu.getMenuId());
        dto.setMenuName(menu.getMenuName());
        dto.setPrice(menu.getPrice());
        dto.setMenuImage(menu.getMenuImage());
        dto.setDescription(menu.getMenuDescription()); // MenuエンティティのmenuDescriptionをマッピング
        dto.setIsSoldOut(menu.getIsSoldOut());

     // TaxRate関連のマッピング
        if (menu.getTaxRate() != null) {
            dto.setTaxRateId(menu.getTaxRate().getTaxRateId());
            dto.setTaxRateValue(menu.getTaxRate().getRate());
            double rate = menu.getTaxRate().getRate();
            
            // ★ここを修正！Math.round() で四捨五入して、結果をDoubleにキャスト
            dto.setPriceWithTax((double) Math.round(menu.getPrice() * (1 + rate))); 
        } else {
            dto.setTaxRateId(null);
            dto.setTaxRateValue(0.0);
            dto.setPriceWithTax(menu.getPrice()); // 税率がない場合は税抜き価格をそのまま
        }

        // ★ MenuGroup関連のマッピング (MenuGroupエンティティから必要な情報をDTOへ)
        if (menu.getMenuGroup() != null) {
            dto.setMenuGroupId(menu.getMenuGroup().getGroupId());
            dto.setMenuGroupName(menu.getMenuGroup().getGroupName());
            dto.setMenuGroupIsPlanTarget(menu.getMenuGroup().getIsPlanTarget());
            dto.setMenuGroupSortOrder(menu.getMenuGroup().getSortOrder());
        } else {
            // MenuGroupがnullの場合のデフォルト値
            dto.setMenuGroupId(null);
            dto.setMenuGroupName(null);
            dto.setMenuGroupIsPlanTarget(false); // デフォルトでfalseなど
            dto.setMenuGroupSortOrder(Integer.MAX_VALUE); // ソート順の末尾に
        }

        // ★ 飲み放題関連の新しいフィールドをDTOにセット
        dto.setIsPlanStarter(menu.getIsPlanStarter());
        dto.setPlanId(menu.getPlanId());
        
        // オプショングループの構築 (ここではMenuOptionのリストを想定)
        // menuOptionRepository.findByMenu_MenuId(menu.getMenuId()) は List<MenuOption> を返すことを想定
        var menuOptions = menuOptionRepository.findByMenu_MenuId(menu.getMenuId());
        List<OptionGroupDTO> groupDTOs = menuOptions.stream()
            .map(menuOption -> {
                // MenuOptionからOptionGroupエンティティを直接取得できれば良い
                // もしOptionGroupIdしか持たないなら、optionGroupRepository.findById(menuOption.getOptionGroupId()) で取得
                var group = optionGroupRepository.findById(menuOption.getOptionGroupId()).orElse(null); // OptionGroupエンティティをOptionGroupRepositoryから取得
                if (group == null) return null; // グループが見つからなければスキップ

                var groupDTO = new OptionGroupDTO();
                groupDTO.setOptionGroupId(group.getOptionGroupId());
                groupDTO.setGroupName(group.getGroupName());

                // OptionItemのリストをOptionItemDTOに変換 (optionItemRepositoryから取得)
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
            .filter(group -> group != null) // nullになったDTOを除外
            .collect(Collectors.toList());

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
        return menuRepository.findById(menuId).map(menu -> {
            menu.setIsSoldOut(isSoldOut);
            return menuRepository.save(menu);
        }).orElse(null);
    }

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