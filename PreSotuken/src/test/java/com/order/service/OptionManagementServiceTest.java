package com.order.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.order.dto.OptionDeletionCheckDTO;
import com.order.entity.Menu;
import com.order.entity.MenuOption;
import com.order.repository.MenuOptionRepository;
import com.order.repository.OptionGroupRepository;
import com.order.repository.OptionItemRepository;

@ExtendWith(MockitoExtension.class)
class OptionManagementServiceTest {

    @Mock
    private OptionGroupRepository optionGroupRepository;
    
    @Mock
    private OptionItemRepository optionItemRepository;
    
    @Mock
    private MenuOptionRepository menuOptionRepository;
    
    @InjectMocks
    private OptionManagementService optionManagementService;
    
    private MenuOption menuOption1;
    private MenuOption menuOption2;
    private Menu menu1;
    private Menu menu2;
    
    @BeforeEach
    void setUp() {
        // テストデータの準備
        menu1 = new Menu();
        menu1.setMenuId(1);
        menu1.setMenuName("ハンバーガー");
        
        menu2 = new Menu();
        menu2.setMenuId(2);
        menu2.setMenuName("チーズバーガー");
        
        menuOption1 = new MenuOption();
        menuOption1.setId(1);
        menuOption1.setMenu(menu1);
        menuOption1.setOptionGroupId(100);
        
        menuOption2 = new MenuOption();
        menuOption2.setId(2);
        menuOption2.setMenu(menu2);
        menuOption2.setOptionGroupId(100);
    }
    
    @Test
    @DisplayName("関連メニューがある場合のオプショングループ削除チェック")
    void testCheckOptionGroupDeletion_WithLinkedMenus() {
        // Given
        int optionGroupId = 100;
        List<MenuOption> linkedMenuOptions = Arrays.asList(menuOption1, menuOption2);
        when(menuOptionRepository.findByOptionGroupIdWithMenu(optionGroupId))
            .thenReturn(linkedMenuOptions);
        
        // When
        OptionDeletionCheckDTO result = optionManagementService.checkOptionGroupDeletion(optionGroupId);
        
        // Then
        assertTrue(result.isHasLinkedMenus());
        assertEquals(2, result.getLinkedMenus().size());
        assertEquals("ハンバーガー", result.getLinkedMenus().get(0).getMenuName());
        assertEquals("チーズバーガー", result.getLinkedMenus().get(1).getMenuName());
        assertEquals(Integer.valueOf(1), result.getLinkedMenus().get(0).getMenuId());
        assertEquals(Integer.valueOf(2), result.getLinkedMenus().get(1).getMenuId());
    }
    
    @Test
    @DisplayName("関連メニューがない場合のオプショングループ削除チェック")
    void testCheckOptionGroupDeletion_NoLinkedMenus() {
        // Given
        int optionGroupId = 200;
        when(menuOptionRepository.findByOptionGroupIdWithMenu(optionGroupId))
            .thenReturn(Arrays.asList());
        
        // When
        OptionDeletionCheckDTO result = optionManagementService.checkOptionGroupDeletion(optionGroupId);
        
        // Then
        assertFalse(result.isHasLinkedMenus());
        assertTrue(result.getLinkedMenus().isEmpty());
    }
    
    @Test
    @DisplayName("重複するメニューがある場合は重複排除される")
    void testCheckOptionGroupDeletion_DuplicateMenusRemoved() {
        // Given
        int optionGroupId = 100;
        
        // 同じメニューに対して複数のMenuOptionが存在する場合
        MenuOption menuOption3 = new MenuOption();
        menuOption3.setId(3);
        menuOption3.setMenu(menu1); // menu1と同じメニュー
        menuOption3.setOptionGroupId(100);
        
        List<MenuOption> linkedMenuOptions = Arrays.asList(menuOption1, menuOption2, menuOption3);
        when(menuOptionRepository.findByOptionGroupIdWithMenu(optionGroupId))
            .thenReturn(linkedMenuOptions);
        
        // When
        OptionDeletionCheckDTO result = optionManagementService.checkOptionGroupDeletion(optionGroupId);
        
        // Then
        assertTrue(result.isHasLinkedMenus());
        assertEquals(2, result.getLinkedMenus().size()); // 重複排除されて2つになる
    }
    
    @Test
    @DisplayName("メニューオプションを含むオプショングループ削除")
    void testDeleteOptionGroupWithMenuOptions() {
        // Given
        int optionGroupId = 100;
        List<MenuOption> menuOptions = Arrays.asList(menuOption1, menuOption2);
        when(menuOptionRepository.findByOptionGroupId(optionGroupId))
            .thenReturn(menuOptions);
        when(optionItemRepository.findByOptionGroupId(optionGroupId))
            .thenReturn(Arrays.asList());
        
        // When
        optionManagementService.deleteOptionGroupWithMenuOptions(optionGroupId);
        
        // Then
        verify(menuOptionRepository).findByOptionGroupId(optionGroupId);
        verify(menuOptionRepository).deleteAll(menuOptions);
        verify(optionItemRepository).findByOptionGroupId(optionGroupId);
        verify(optionGroupRepository).deleteById(optionGroupId);
    }
}