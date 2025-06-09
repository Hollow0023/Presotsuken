package com.order.service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // トランザクション管理
import org.springframework.web.multipart.MultipartFile;

import com.order.entity.Menu;
import com.order.entity.MenuOption;
import com.order.entity.MenuPrinterMap;
import com.order.entity.PrinterConfig;
import com.order.entity.Store;
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

    /**
     * 新しいメニューを追加し、画像、プリンター、オプションとの紐付けを行う。
     *
     * @param menu メニューエンティティ
     * @param imageFile メニュー画像ファイル
     * @param optionGroupIds 選択されたオプショングループIDのリスト
     * @param printerIds 選択されたプリンターIDのリスト
     * @param storeId 店舗ID
     * @return 保存されたMenuエンティティ
     * @throws IOException ファイル操作エラーが発生した場合
     * @throws IllegalArgumentException 店舗情報が見つからない場合など、不正な引数があった場合
     */
    @Transactional // このメソッド全体をトランザクションで管理する
    public Menu addNewMenu(Menu menu, MultipartFile imageFile,
                           List<Integer> optionGroupIds, List<Integer> printerIds, Integer storeId) throws IOException {

        Optional<Store> optionalStore = storeRepository.findById(storeId);
        if (optionalStore.isEmpty()) {
            throw new IllegalArgumentException("店舗情報が見つかりませんでした。");
        }

        // 1. 画像アップロード
        if (!imageFile.isEmpty()) {
            String imagePath = imageUploadService.uploadImage(imageFile, storeId);
            menu.setMenuImage(imagePath);
        }

        // 2. 基本情報のセット
        menu.setStore(optionalStore.get());
        menu.setIsSoldOut(false);
        if (menu.getReceiptLabel() == null || menu.getReceiptLabel().trim().isEmpty()) {
            menu.setReceiptLabel(menu.getMenuName());
        }

        // 3. メニュー本体を保存 (新規の場合はIDがここで生成される)
        Menu savedMenu = menuRepository.save(menu);
        
        // 4. プリンターの紐付け
        // 既存の紐付けを全て削除（更新の場合にも対応できるよう、常に削除→再作成）
        // Menuの主キーがmenuIdなので、deleteByMenu_MenuId を使う
        menuPrinterMapRepository.deleteByMenu_MenuId(savedMenu.getMenuId()); 

        if (printerIds != null && !printerIds.isEmpty()) {
            List<MenuPrinterMap> mapsToSave = printerIds.stream()
                .map(printerId -> {
                    if (printerId == null) return null; // nullチェック
                    Optional<PrinterConfig> printerOptional = printerConfigRepository.findById(printerId);
                    if (printerOptional.isPresent()) {
                        return new MenuPrinterMap(savedMenu, printerOptional.get());
                    } else {
                        System.err.println("Warning: Printer with ID " + printerId + " not found for menu " + savedMenu.getMenuId());
                        return null;
                    }
                })
                .filter(map -> map != null) // nullを除外
                .collect(Collectors.toList());
            
            if (!mapsToSave.isEmpty()) {
                menuPrinterMapRepository.saveAll(mapsToSave);
            }
        }
        
        // 5. オプションの紐付け
        // ここも更新時に対応するため、既存のオプション紐付けを削除してから再作成するロジックを推奨
        // MenuOptionRepositoryに void deleteByMenuId(Integer menuId); メソッドが必要
        menuOptionRepository.deleteByMenuId(savedMenu.getMenuId()); // このメソッドがMenuOptionRepositoryにある前提

        if (optionGroupIds != null) {
            for (Integer groupId : optionGroupIds) {
                if (groupId == null) continue;
                MenuOption mog = new MenuOption();
                mog.setMenuId(savedMenu.getMenuId()); // 保存されたメニューのIDを使用
                mog.setOptionGroupId(groupId);
                menuOptionRepository.save(mog);
            }
        }
        
        return savedMenu; // 保存されたメニューを返す
    }
}