package com.order.service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.order.entity.Menu;
import com.order.entity.MenuGroup;
import com.order.entity.MenuOption;
import com.order.entity.MenuPrinterMap;
import com.order.entity.PrinterConfig;
import com.order.entity.Store;
import com.order.repository.MenuGroupRepository;
import com.order.repository.MenuOptionRepository;
import com.order.repository.MenuPrinterMapRepository;
import com.order.repository.MenuRepository;
import com.order.repository.PrinterConfigRepository;
import com.order.repository.StoreRepository;

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
    
    // ★ 顧客向け注文画面に表示するメニューグループを取得するメソッド
    public List<MenuGroup> getCustomerMenuGroups(Integer storeId) {
        // forAdminOnlyがfalseまたはnullのメニューグループのみを返す
        return menuGroupRepository.findByStore_StoreIdAndForAdminOnlyFalseOrForAdminOnlyIsNull(storeId);
    }

    // ★ 管理者向け注文画面に表示するメニューグループを取得するメソッド
    public List<MenuGroup> getAdminMenuGroups(Integer storeId) {
        // 全てのメニューグループを返す（既存のfindByStore_StoreIdと同じ）
        return menuGroupRepository.findByStore_StoreId(storeId);
    }

    // 全メニューを取得するメソッド
    public List<Menu> getMenusByStoreId(Integer storeId) {
        // Eager LoadingまたはFetch Joinが必要な場合、Repositoryに専用メソッドを追加する
        // return menuRepository.findByStore_StoreId(storeId);
        // ★関連エンティティも取得したい場合、Fetch Joinを使う
        // 例: return menuRepository.findByStore_StoreIdWithDetails(storeId);
        // MenuOptionとMenuPrinterMapをEager Loadingに設定した場合、単にfindByStore_StoreIdでOK
        return menuRepository.findByStore_StoreId(storeId);
    }

    // 特定のメニューを取得するメソッド (関連データもフェッチされるようにエンティティを調整)
    public Optional<Menu> getMenuById(Integer menuId) {
        // ここでも関連データ（オプションやプリンター）が取得されるように設定が必要
        // Menuエンティティの@OneToManyにFetchType.EAGERを設定するか、
        // Repositoryにfetch joinのクエリメソッドを追加する
        // 例: return menuRepository.findByIdWithDetails(menuId);
        return menuRepository.findById(menuId); // Eager Loading前提
    }
    
    // 特定のメニューに紐づくオプションIDを取得するメソッド
    public List<Integer> getMenuOptionIdsByMenuId(Integer menuId) {
        // MenuエンティティにList<MenuOption>があれば、menu.getMenuOptions()で取得できるので、
        // このメソッドは不要になる可能性もある。
        // もしMenuOptionがMenuへの参照(ManyToOne)を持たない（menuIdを直接持つ）なら、このメソッドは必要
        return menuOptionRepository.findByMenu_MenuId(menuId).stream()
            .map(MenuOption::getOptionGroupId)
            .collect(Collectors.toList());
    }

    // 特定のメニューに紐づくプリンターIDを取得するメソッド
    public List<Integer> getMenuPrinterIdsByMenuId(Integer menuId) {
        // MenuエンティティにList<MenuPrinterMap>があれば、menu.getMenuPrinterMaps()で取得できる
        // その場合、このメソッドは不要になる。
        return menuPrinterMapRepository.findByMenu_MenuId(menuId).stream()
            .map(mpm -> mpm.getPrinter().getPrinterId())
            .collect(Collectors.toList());
    }


    /**
     * 新しいメニューを追加し、画像、プリンター、オプションとの紐付けを行う。
     * @param menu メニューエンティティ
     * @param imageFile アップロードされた画像ファイル
     * @param existingMenuImage 既存の画像パス (新規作成時はnull)
     * @param optionGroupIds オプショングループIDのリスト
     * @param printerIds プリンターIDのリスト
     * @param storeId 店舗ID
     * @return 保存されたMenuエンティティ
     * @throws IOException
     * @throws IllegalArgumentException
     */
    @Transactional
    public Menu addNewMenu(Menu menu, MultipartFile imageFile, String existingMenuImage, // ★existingMenuImage引数を追加
                           List<Integer> optionGroupIds, List<Integer> printerIds, Integer storeId) throws IOException {

        Optional<Store> optionalStore = storeRepository.findById(storeId);
        if (optionalStore.isEmpty()) {
            throw new IllegalArgumentException("店舗情報が見つかりませんでした。");
        }

        // 画像の処理
        if (!imageFile.isEmpty()) {
            // 新しい画像がアップロードされた場合
            String imagePath = imageUploadService.uploadImage(imageFile, storeId);
            menu.setMenuImage(imagePath);
        } else if (existingMenuImage != null && !existingMenuImage.isEmpty()) {
            // 新しい画像はなく、既存の画像パスがある場合（＝変更なし）
            menu.setMenuImage(existingMenuImage);
        } else {
            // 画像が削除された場合（または元々ない場合）
            menu.setMenuImage(null);
        }

        menu.setStore(optionalStore.get());
        menu.setIsSoldOut(false);
        if (menu.getReceiptLabel() == null || menu.getReceiptLabel().trim().isEmpty()) {
            menu.setReceiptLabel(menu.getMenuName());
        }

        Menu savedMenu = menuRepository.save(menu);
        
        // プリンターの紐付け
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
        
        // オプションの紐付け
        menuOptionRepository.deleteByMenu_MenuId(savedMenu.getMenuId()); 
        if (optionGroupIds != null) {
            for (Integer groupId : optionGroupIds) {
                if (groupId == null) continue;
                MenuOption mog = new MenuOption();
                // mog.setMenuId(savedMenu.getMenuId()); // ★この行を削除
                mog.setMenu(savedMenu); // ★この行を追加：Menuエンティティを設定
                mog.setOptionGroupId(groupId);
                menuOptionRepository.save(mog);
            }
        }
        
        return savedMenu;
    }


    /**
     * 既存のメニューを更新し、画像、プリンター、オプションとの紐付けを行う。
     * @param menu 更新するメニューエンティティ（menuIdがセットされていること）
     * @param imageFile 新しいメニュー画像ファイル（空の場合、既存の画像は保持）
     * @param existingMenuImage 既存の画像パス (imageFileが空でこれが渡された場合)
     * @param optionGroupIds 選択されたオプショングループIDのリスト
     * @param printerIds 選択されたプリンターIDのリスト
     * @param storeId 店舗ID
     * @return 更新されたMenuエンティティ
     * @throws IOException
     * @throws IllegalArgumentException
     */
    @Transactional
    public Menu updateExistingMenu(Menu menu, MultipartFile imageFile, String existingMenuImage, // ★existingMenuImage引数を追加
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

        // 画像の処理
        if (!imageFile.isEmpty()) {
            // 新しい画像ファイルがアップロードされた場合
            // 古い画像を削除
            if (existingMenu.getMenuImage() != null && !existingMenu.getMenuImage().isEmpty()) {
                imageUploadService.deleteImage(existingMenu.getMenuImage());
            }
            String newImagePath = imageUploadService.uploadImage(imageFile, storeId);
            existingMenu.setMenuImage(newImagePath);
        } else if (existingMenuImage != null && !existingMenuImage.isEmpty()) {
            // 新しい画像はないが、既存の画像パスがある場合（＝変更なし）
            existingMenu.setMenuImage(existingMenuImage);
        } else {
            // 画像が削除された場合 (existingMenuImageがnullまたは空)
            // 古い画像を削除
            if (existingMenu.getMenuImage() != null && !existingMenu.getMenuImage().isEmpty()) {
                imageUploadService.deleteImage(existingMenu.getMenuImage());
            }
            existingMenu.setMenuImage(null);
        }

        // その他のプロパティを更新
        existingMenu.setMenuName(menu.getMenuName());
        existingMenu.setPrice(menu.getPrice());
        existingMenu.setMenuDescription(menu.getMenuDescription());
        existingMenu.setIsSoldOut(menu.getIsSoldOut());
        existingMenu.setReceiptLabel(menu.getReceiptLabel());
        
        // 関連エンティティはIDがセットされていれば自動でマッピングされる
        // ただし、完全に新しいTaxRate/MenuGroup/MenuTimeSlotのインスタンスを渡すのではなく、
        // 既存のDBからのインスタンス（またはIDのみセットされた参照）を設定する必要がある。
        // そうしないと、意図せず新しいレコードが作成されたり、TransientObjectExceptionが発生する可能性あり。
        // コントローラからのmenuオブジェクトの関連エンティティにはIDのみがセットされていると想定。
        // もし必要なら、RepositoryからTaxRateなどをfindByIdで取得してセットし直す。
        existingMenu.setTaxRate(menu.getTaxRate()); 
        existingMenu.setMenuGroup(menu.getMenuGroup());
        existingMenu.setTimeSlot(menu.getTimeSlot());


        Menu updatedMenu = menuRepository.save(existingMenu); // saveで更新

        // プリンターの紐付け
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
        
        // オプションの紐付け
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

    // メニュー削除メソッド (変更なし)
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