package com.order.service;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.order.dto.MenuForm;
import com.order.dto.MenuWithOptionsDTO;
import com.order.entity.Menu;
import com.order.entity.MenuGroup;
import com.order.entity.MenuOption;
import com.order.entity.MenuPrinterMap;
import com.order.entity.Payment;
import com.order.entity.PaymentDetail;
import com.order.entity.PrinterConfig;
import com.order.entity.Store;
import com.order.entity.Visit;
import com.order.repository.MenuGroupRepository;
import com.order.repository.MenuOptionRepository;
import com.order.repository.MenuPrinterMapRepository;
import com.order.repository.MenuRepository;
import com.order.repository.PaymentDetailRepository;
import com.order.repository.PaymentRepository;
import com.order.repository.PlanMenuGroupMapRepository;
import com.order.repository.PlanRepository;
import com.order.repository.PrinterConfigRepository;
import com.order.repository.StoreRepository;
import com.order.repository.VisitRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MenuAddService {

    private final MenuRepository menuRepository;
    private final ImageUploadService imageUploadService;
    private final StoreRepository storeRepository;
    private final MenuPrinterMapRepository menuPrinterMapRepository;
    private final PrinterConfigRepository printerConfigRepository;
    private final MenuOptionRepository menuOptionRepository;
    private final MenuGroupRepository menuGroupRepository;

    private final PaymentDetailRepository paymentDetailRepository;
    private final PaymentRepository paymentRepository;
    private final VisitRepository visitRepository;
    private final PlanRepository planRepository;
    private final PlanMenuGroupMapRepository planMenuGroupMapRepository;
    
    
    // MenuエンティティをMenuForm DTOに変換して返すメソッド
    public Optional<MenuForm> getMenuFormById(Integer menuId) {
        return menuRepository.findById(menuId)
                .map(menu -> {
                    MenuForm form = new MenuForm();
                    form.setMenuId(menu.getMenuId());
                    form.setMenuName(menu.getMenuName());
                    form.setMenuImage(menu.getMenuImage());
                    form.setPrice(menu.getPrice());
                    form.setMenuDescription(menu.getMenuDescription());
                    form.setReceiptLabel(menu.getReceiptLabel());
                    form.setIsSoldOut(menu.getIsSoldOut());

                    // ★★★ここから修正！関連エンティティのフィールドをIDと表示名に置き換え★★★
                    if (menu.getTimeSlot() != null) {
                        form.setTimeSlotTimeSlotId(menu.getTimeSlot().getTimeSlotId());
                        form.setTimeSlotName(menu.getTimeSlot().getName());
                        form.setTimeSlotStartTime(menu.getTimeSlot().getStartTime().toString()); // LocalTimeをStringに
                        form.setTimeSlotEndTime(menu.getTimeSlot().getEndTime().toString());   // LocalTimeをStringに
                    }
                    if (menu.getTaxRate() != null) {
                        form.setTaxRateTaxRateId(menu.getTaxRate().getTaxRateId());
                        form.setTaxRateRate(menu.getTaxRate().getRate());
                    }
                    if (menu.getMenuGroup() != null) {
                        form.setMenuGroupGroupId(menu.getMenuGroup().getGroupId());
                        form.setMenuGroupName(menu.getMenuGroup().getGroupName());
                    }
                    
                    form.setIsPlanStarter(menu.getIsPlanStarter());
                    form.setPlanId(menu.getPlanId());

                    // オプションとプリンターのIDリストを取得してセット！
                    List<Integer> optionGroupIds = menuOptionRepository.findByMenu_MenuId(menuId).stream()
                        .map(MenuOption::getOptionGroupId)
                        .collect(Collectors.toList());
                    form.setOptionGroupIds(optionGroupIds);

                    List<Integer> printerIds = menuPrinterMapRepository.findByMenu_MenuId(menuId).stream()
                        .map(mpm -> mpm.getPrinter().getPrinterId())
                        .collect(Collectors.toList());
                    form.setPrinterIds(printerIds);

                    return form;
                });
    }


    
    // ★ 顧客向け注文画面に表示するメニューグループを取得するメソッド (ソート順適用)
    public List<MenuGroup> getCustomerMenuGroups(Integer storeId) {
        // forAdminOnlyがfalseまたはnull、かつ isPlanTargetがfalseのメニューグループのみを返す
        // ※このメソッド名だと、`findByStore_StoreIdAndForAdminOnlyFalseOrForAdminOnlyIsNullAndIsPlanTargetFalseOrderBySortOrderAsc`
        //   のような長い名前のメソッドが必要になる可能性があるので、リポジトリに合わせて適切なものを選択
        return menuGroupRepository.findByStore_StoreIdAndIsPlanTargetFalseAndForAdminOnlyFalseOrForAdminOnlyIsNullOrderBySortOrderAsc(storeId);
    }

    // ★ 管理者向け注文画面に表示するメニューグループを取得するメソッド (ソート順適用)
    public List<MenuGroup> getAdminMenuGroups(Integer storeId) {
        // 全てのメニューグループをsort_orderでソートして返す
        return menuGroupRepository.findByStore_StoreIdOrderBySortOrderAsc(storeId);
    }

    // ★ 追加：飲み放題がアクティブな場合に表示する顧客向けメニューグループを取得 (ソート順適用)
    public List<MenuGroup> getPlanActivatedCustomerMenuGroups(Integer storeId, Integer seatId) {
        Set<MenuGroup> combinedGroups = new HashSet<>();

        // 1. 通常の顧客向けメニューグループ（isPlanTarget=false）を追加 (ソート順適用)
        combinedGroups.addAll(getCustomerMenuGroups(storeId)); // このメソッドがソート済みを返すのでOK

        // 2. 現在アクティブな飲み放題プランがあるかチェックし、そのplanIdを取得
        Integer activePlanId = getActivePlanIdForSeat(seatId, storeId);

        if (activePlanId != null) {
            List<Integer> planTargetMenuGroupIds = planMenuGroupMapRepository.findByPlanId(activePlanId).stream()
                .map(map -> map.getMenuGroupId())
                .collect(Collectors.toList());
            
            // 取得したMenuGroupIdリストを使ってMenuGroupエンティティを取得 (ソート順適用)
            List<MenuGroup> planTargetGroups = menuGroupRepository.findByGroupIdInAndIsPlanTargetTrueOrderBySortOrderAsc(planTargetMenuGroupIds);
            combinedGroups.addAll(planTargetGroups);
        }

        // SetからListに変換後、最終的なソート順を保証するために再度ソート（もし必要なら）
        // ただし、個々のリストがソートされていれば、HashSetに入れても順序は失われるので
        // 最終的なリストをソートする方が確実
        return combinedGroups.stream()
                             .sorted( (g1, g2) -> {
                                 // sortOrderがnullの場合を考慮する
                                 Integer order1 = g1.getSortOrder() != null ? g1.getSortOrder() : Integer.MAX_VALUE;
                                 Integer order2 = g2.getSortOrder() != null ? g2.getSortOrder() : Integer.MAX_VALUE;
                                 return order1.compareTo(order2);
                             })
                             .collect(Collectors.toList());
    }

    // ヘルパーメソッド: 特定のシートでアクティブな飲み放題プランのIDを取得する
    public Integer getActivePlanIdForSeat(Integer seatId, Integer storeId) {
        Visit currentVisit = visitRepository.findTopByStore_StoreIdAndSeat_SeatIdOrderByVisitTimeDesc(storeId, seatId);
        if (currentVisit == null) {
            return null;
        }

        Payment payment = paymentRepository.findByVisitVisitId(currentVisit.getVisitId());
        if (payment == null) {
            return null;
        }

        List<PaymentDetail> planStarterOrders = paymentDetailRepository.findByPaymentPaymentIdAndMenuIsPlanStarterTrue(payment.getPaymentId());

        if (!planStarterOrders.isEmpty()) {
            return planStarterOrders.get(0).getMenu().getPlanId();
        }
        return null;
    }


    // 全メニューを取得するメソッド (menu_nameでソート適用)
    public List<Menu> getMenusByStoreId(Integer storeId) {
        return menuRepository.findByStore_StoreIdOrderByMenuNameAsc(storeId);
    }
    // ...getMenusWithOptions (品切れ表示しない場合)
       public List<MenuWithOptionsDTO> getMenusWithOptions(Integer storeId) {
           List<Menu> menus = menuRepository.findByStore_StoreIdAndIsSoldOutFalseOrderByMenuNameAsc(storeId);
           // DTOへの変換ロジック
           return menus.stream()
               .map(menu -> new MenuWithOptionsDTO(menu)) // コンストラクタでDTOに変換
               .collect(Collectors.toList());
       }

    // ...getAllMenusWithOptions (品切れも表示する場合)
    //   public List<MenuWithOptionsDTO> getAllMenusWithOptions(Integer storeId) {
    //       List<Menu> menus = menuRepository.findByStore_StoreIdOrderByMenuNameAsc(storeId);
    //       // DTOへの変換ロジック
    //       return menus.stream()
    //           .map(menu -> new MenuWithOptionsDTO(menu))
    //           .collect(Collectors.toList());
    //   }


    // 特定のメニューを取得するメソッド (関連データもフェッチされるようにエンティティを調整)
    public Optional<Menu> getMenuById(Integer menuId) {
        return menuRepository.findById(menuId);
    }
    
    public List<Integer> getMenuOptionIdsByMenuId(Integer menuId) {
        return menuOptionRepository.findByMenu_MenuId(menuId).stream()
            .map(MenuOption::getOptionGroupId)
            .collect(Collectors.toList());
    }

    public List<Integer> getMenuPrinterIdsByMenuId(Integer menuId) {
        return menuPrinterMapRepository.findByMenu_MenuId(menuId).stream()
            .map(mpm -> mpm.getPrinter().getPrinterId())
            .collect(Collectors.toList());
    }


    @Transactional
    public Menu addNewMenu(Menu menu, MultipartFile imageFile, String existingMenuImage,
                            List<Integer> optionGroupIds, List<Integer> printerIds, Integer storeId) throws IOException {

        Optional<Store> optionalStore = storeRepository.findById(storeId);
        if (optionalStore.isEmpty()) {
            throw new IllegalArgumentException("店舗情報が見つかりませんでした。");
        }
        if (!imageFile.isEmpty()) {
            String imagePath = imageUploadService.uploadImage(imageFile, storeId);
            menu.setMenuImage(imagePath);
        } else if (existingMenuImage != null && !existingMenuImage.isEmpty()) {
            menu.setMenuImage(existingMenuImage);
        } else {
            menu.setMenuImage(null);
        }

        menu.setStore(optionalStore.get());
        menu.setIsSoldOut(false);
        if (menu.getReceiptLabel() == null || menu.getReceiptLabel().trim().isEmpty()) {
            menu.setReceiptLabel(menu.getMenuName());
        }

        // ★新規メニューのsortOrder初期値設定（管理画面で設定しない場合）
        //   例: 現在の最大値+1にするか、0などデフォルト値にする
        //   もし管理画面で設定するならここでの設定は不要
        // menu.setSortOrder(0); // 仮の初期値

        Menu savedMenu = menuRepository.save(menu);
        
        menuPrinterMapRepository.deleteByMenu_MenuId(savedMenu.getMenuId()); 
        if (printerIds != null && !printerIds.isEmpty()) {
            List<MenuPrinterMap> mapsToSave = printerIds.stream()
                .map(printerId -> {
                    if (printerId == null) return null;
                    Optional<PrinterConfig> printerOptional = printerConfigRepository.findById(printerId);
                    if (printerOptional.isPresent()) {
                        return new MenuPrinterMap(savedMenu, printerOptional.get());
                    } else {
                        System.err.println("Warning: Printer with ID " + printerId + " not found for menu " + savedMenu.getMenuId());
                        return null;
                    }
                })
                .filter(map -> map != null)
                .collect(Collectors.toList());
            
            if (!mapsToSave.isEmpty()) {
                menuPrinterMapRepository.saveAll(mapsToSave);
            }
        }
        
        menuOptionRepository.deleteByMenu_MenuId(savedMenu.getMenuId()); 
        if (optionGroupIds != null) {
            for (Integer groupId : optionGroupIds) {
                if (groupId == null) continue;
                MenuOption mog = new MenuOption();
                mog.setMenu(savedMenu);
                mog.setOptionGroupId(groupId);
                menuOptionRepository.save(mog);
            }
        }
        
        return savedMenu;
    }


    @Transactional
    public Menu updateExistingMenu(Menu menu, MultipartFile imageFile, String existingMenuImage,
                                    List<Integer> optionGroupIds, List<Integer> printerIds, Integer storeId) throws IOException {

        if (menu.getMenuId() == null) {
            throw new IllegalArgumentException("更新対象のメニューIDが指定されていません。");
        }

        Optional<Menu> existingMenuOpt = menuRepository.findById(menu.getMenuId());
        if (existingMenuOpt.isEmpty()) {
            throw new IllegalArgumentException("指定されたメニューが見つかりませんでした。");
        }
        Menu existingMenu = existingMenuOpt.get();

        if (!existingMenu.getStore().getStoreId().equals(storeId)) {
            throw new IllegalArgumentException("指定されたメニューは現在の店舗に属していません。");
        }

        if (!imageFile.isEmpty()) {
            if (existingMenu.getMenuImage() != null && !existingMenu.getMenuImage().isEmpty()) {
                imageUploadService.deleteImage(existingMenu.getMenuImage());
            }
            String newImagePath = imageUploadService.uploadImage(imageFile, storeId);
            existingMenu.setMenuImage(newImagePath);
        } else if (existingMenuImage != null && !existingMenuImage.isEmpty()) {
            existingMenu.setMenuImage(existingMenuImage);
        } else {
            if (existingMenu.getMenuImage() != null && !existingMenu.getMenuImage().isEmpty()) {
                imageUploadService.deleteImage(existingMenu.getMenuImage());
            }
            existingMenu.setMenuImage(null);
        }
        
        existingMenu.setIsPlanStarter(menu.getIsPlanStarter()); 
        existingMenu.setPlanId(menu.getPlanId());       

        existingMenu.setMenuName(menu.getMenuName());
        existingMenu.setPrice(menu.getPrice());
        existingMenu.setMenuDescription(menu.getMenuDescription());
        existingMenu.setIsSoldOut(menu.getIsSoldOut());
        existingMenu.setReceiptLabel(menu.getReceiptLabel());
        
        existingMenu.setTaxRate(menu.getTaxRate()); 
        existingMenu.setMenuGroup(menu.getMenuGroup());
        existingMenu.setTimeSlot(menu.getTimeSlot());

        // sortOrderは管理者画面からのみ設定されることを想定し、ここでは変更しない
        // existingMenu.setSortOrder(menu.getSortOrder()); // 必要なら追加

        Menu updatedMenu = menuRepository.save(existingMenu);
        
        menuPrinterMapRepository.deleteByMenu_MenuId(updatedMenu.getMenuId()); 
        if (printerIds != null && !printerIds.isEmpty()) {
            List<MenuPrinterMap> mapsToSave = printerIds.stream()
                .map(printerId -> {
                    if (printerId == null) return null;
                    Optional<PrinterConfig> printerOptional = printerConfigRepository.findById(printerId);
                    if (printerOptional.isPresent()) {
                        return new MenuPrinterMap(updatedMenu, printerOptional.get());
                    } else {
                        System.err.println("Warning: Printer with ID " + printerId + " not found for menu " + updatedMenu.getMenuId());
                        return null;
                    }
                })
                .filter(map -> map != null)
                .collect(Collectors.toList());
            
            if (!mapsToSave.isEmpty()) {
                menuPrinterMapRepository.saveAll(mapsToSave);
            }
        }
        
        menuOptionRepository.deleteByMenu_MenuId(updatedMenu.getMenuId()); 
        if (optionGroupIds != null) {
            for (Integer groupId : optionGroupIds) {
                if (groupId == null) continue;
                MenuOption mog = new MenuOption();
                mog.setMenu(updatedMenu);
                mog.setOptionGroupId(groupId);
                menuOptionRepository.save(mog);
            }
        }

        return updatedMenu;
    }

    @Transactional
    public void deleteMenu(Integer menuId, Integer storeId) {
        Optional<Menu> menuOpt = menuRepository.findById(menuId);
        if (menuOpt.isEmpty()) {
            throw new IllegalArgumentException("指定されたメニューが見つかりませんでした。");
        }
        Menu menuToDelete = menuOpt.get();

        if (!menuToDelete.getStore().getStoreId().equals(storeId)) {
            throw new IllegalArgumentException("指定されたメニューは現在の店舗に属していません。");
        }

        menuPrinterMapRepository.deleteByMenu_MenuId(menuToDelete.getMenuId());
        menuOptionRepository.deleteByMenu_MenuId(menuToDelete.getMenuId());

        if (menuToDelete.getMenuImage() != null && !menuToDelete.getMenuImage().isEmpty()) {
            try {
                imageUploadService.deleteImage(menuToDelete.getMenuImage());
            } catch (IOException e) {
                System.err.println("Warning: Failed to delete image file for menu " + menuId + ": " + e.getMessage());
            }
        }
        menuRepository.delete(menuToDelete);
    }
}