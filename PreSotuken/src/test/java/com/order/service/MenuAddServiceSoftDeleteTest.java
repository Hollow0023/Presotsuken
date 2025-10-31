package com.order.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.order.entity.Menu;
import com.order.entity.Store;
import com.order.repository.MenuOptionRepository;
import com.order.repository.MenuPrinterMapRepository;
import com.order.repository.MenuRepository;

@ExtendWith(MockitoExtension.class)
public class MenuAddServiceSoftDeleteTest {

    @Mock
    private MenuRepository menuRepository;
    
    @Mock
    private MenuPrinterMapRepository menuPrinterMapRepository;
    
    @Mock
    private MenuOptionRepository menuOptionRepository;
    
    @Mock
    private ImageUploadService imageUploadService;

    @InjectMocks
    private MenuAddService menuAddService;

    @Test
    void testSoftDeleteMenuSuccessfully() {
        // Given
        Integer menuId = 1;
        Integer storeId = 1;
        
        Store store = new Store();
        store.setStoreId(storeId);
        
        Menu menu = new Menu();
        menu.setMenuId(menuId);
        menu.setMenuName("テストメニュー");
        menu.setStore(store);
        menu.setDeletedAt(null); // 削除されていない状態

        when(menuRepository.findById(menuId)).thenReturn(Optional.of(menu));
        when(menuRepository.save(any(Menu.class))).thenAnswer(invocation -> {
            Menu savedMenu = invocation.getArgument(0);
            return savedMenu;
        });

        // When
        menuAddService.deleteMenu(menuId, storeId);

        // Then
        verify(menuRepository).save(menu);
        assertNotNull(menu.getDeletedAt());
        assertTrue(menu.getDeletedAt().isBefore(LocalDateTime.now().plusSeconds(1)));
        
        // プリンターマップやオプションは削除しない（ソフトデリート）
        verify(menuPrinterMapRepository, never()).deleteByMenu_MenuId(menuId);
        verify(menuOptionRepository, never()).deleteByMenu_MenuId(menuId);
    }

    @Test
    void testDeleteAlreadyDeletedMenuThrowsException() {
        // Given
        Integer menuId = 1;
        Integer storeId = 1;
        
        Store store = new Store();
        store.setStoreId(storeId);
        
        Menu menu = new Menu();
        menu.setMenuId(menuId);
        menu.setMenuName("テストメニュー");
        menu.setStore(store);
        menu.setDeletedAt(LocalDateTime.now().minusDays(1)); // 既に削除済み

        when(menuRepository.findById(menuId)).thenReturn(Optional.of(menu));

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> menuAddService.deleteMenu(menuId, storeId)
        );
        
        assertEquals("指定されたメニューは既に削除済みです。", exception.getMessage());
        verify(menuRepository, never()).save(any(Menu.class));
    }

    @Test
    void testDeleteMenuFromDifferentStoreThrowsException() {
        // Given
        Integer menuId = 1;
        Integer storeId = 1;
        Integer differentStoreId = 2;
        
        Store store = new Store();
        store.setStoreId(differentStoreId);
        
        Menu menu = new Menu();
        menu.setMenuId(menuId);
        menu.setMenuName("テストメニュー");
        menu.setStore(store);
        menu.setDeletedAt(null);

        when(menuRepository.findById(menuId)).thenReturn(Optional.of(menu));

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> menuAddService.deleteMenu(menuId, storeId)
        );
        
        assertEquals("指定されたメニューは現在の店舗に属していません。", exception.getMessage());
        verify(menuRepository, never()).save(any(Menu.class));
    }

    @Test
    void testDeleteNonExistentMenuThrowsException() {
        // Given
        Integer menuId = 999;
        Integer storeId = 1;

        when(menuRepository.findById(menuId)).thenReturn(Optional.empty());

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> menuAddService.deleteMenu(menuId, storeId)
        );
        
        assertEquals("指定されたメニューが見つかりませんでした。", exception.getMessage());
        verify(menuRepository, never()).save(any(Menu.class));
    }
}