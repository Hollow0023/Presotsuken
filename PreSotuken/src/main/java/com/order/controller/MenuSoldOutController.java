package com.order.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
// import org.springframework.ui.Model; // 不要なので削除
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
// import org.springframework.web.bind.annotation.RequestParam; // 不要なので削除
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.order.dto.BulkSoldOutStatusRequest;
import com.order.dto.MenuWithOptionsDTO;
import com.order.dto.SoldOutStatusRequest;
import com.order.entity.Menu;
import com.order.service.MenuService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController // RESTful APIを扱うコントローラーのまま
@RequestMapping("/api/admin/menu-sold-out") // API用のパス（/api/を付ける）
@RequiredArgsConstructor
public class MenuSoldOutController {

    private final MenuService menuService;

    // メニュー一覧（品切れ状態含む）を取得するAPI
    // GET /api/admin/menu-sold-out
    @GetMapping
    public ResponseEntity<List<MenuWithOptionsDTO>> getSoldOutStatusMenus(HttpServletRequest request) {
        Integer storeId = getStoreIdFromCookie(request);
        if (storeId == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        List<MenuWithOptionsDTO> menus = menuService.getAllMenusWithOptions(storeId);
        return ResponseEntity.ok(menus);
    }

    // 単一のメニューの品切れ状態を更新するAPI (変更なし)
    // PUT /api/admin/menu-sold-out/{menuId}
    @PutMapping("/{menuId}")
    public ResponseEntity<MenuWithOptionsDTO> updateSingleMenuSoldOutStatus(
            @PathVariable Integer menuId,
            @RequestBody SoldOutStatusRequest request,
            HttpServletRequest httpServletRequest) { 
        
        Integer storeId = getStoreIdFromCookie(httpServletRequest);
        if (storeId == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        Menu updatedMenu = menuService.updateMenuSoldOutStatus(menuId, request.getIsSoldOut());
        if (updatedMenu == null || !updatedMenu.getStore().getStoreId().equals(storeId)) {
            return ResponseEntity.notFound().build(); 
        }
        return ResponseEntity.ok(menuService.toDto(updatedMenu));
    }

    // 複数のメニューの品切れ状態を一括更新するAPI (変更なし)
    // PUT /api/admin/menu-sold-out/bulk
    @PutMapping("/bulk")
    public ResponseEntity<List<MenuWithOptionsDTO>> updateMultipleMenuSoldOutStatus(
            @RequestBody BulkSoldOutStatusRequest request,
            HttpServletRequest httpServletRequest) {

        Integer storeId = getStoreIdFromCookie(httpServletRequest);
        if (storeId == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        List<Menu> updatedMenus = menuService.updateMultipleMenuSoldOutStatus(request.getMenuIds(), request.getIsSoldOut());
        
        List<MenuWithOptionsDTO> updatedDtos = updatedMenus.stream()
            .filter(menu -> menu.getStore().getStoreId().equals(storeId))
            .map(menuService::toDto)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(updatedDtos);
    }
    
    // showSoldOutManagementPage メソッドはここから削除！ (次の項目で別のコントローラーに移動)

    // CookieからstoreIdを取得するヘルパーメソッド (変更なし)
    private Integer getStoreIdFromCookie(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("storeId".equals(cookie.getName())) {
                    try {
                        return Integer.parseInt(cookie.getValue());
                    } catch (NumberFormatException e) {
                        return null;
                    }
                }
            }
        }
        return null;
    }
}