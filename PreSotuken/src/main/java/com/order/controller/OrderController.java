// src/main/java/com/order/controller/OrderController.java (修正版)

package com.order.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.order.dto.MenuWithOptionsDTO;
import com.order.dto.OrderHistoryDto;
import com.order.entity.Menu;
import com.order.entity.MenuGroup;
import com.order.entity.Seat;
import com.order.entity.Store;
import com.order.entity.Visit;
import com.order.service.MenuAddService;
import com.order.service.MenuService;
import com.order.service.OrderService;
import com.order.util.CookieUtil;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * 注文機能に関するコントローラ
 * 注文画面の表示、注文の登録、来店登録などを担当します
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/order")
public class OrderController {

	private final MenuService menuService;
	private final MenuAddService menuAddService;
	private final OrderService orderService;
	private final CookieUtil cookieUtil;

	/**
	 * 注文画面を表示します
	 * 
	 * @param seatIdCookie 座席ID（Cookieから取得）
	 * @param storeId 店舗ID
	 * @param seatIdParam 座席ID（URLパラメータから取得）
	 * @param showAll 全メニューを表示するかどうか（管理者用）
	 * @param model ビューに渡すモデル
	 * @return 注文画面のテンプレート名
	 */
	@GetMapping
	public String showOrderPage(
	        @CookieValue(name = "seatId", required = false) Integer seatIdCookie,
	        @CookieValue(name = "storeId") Integer storeId,
	        @RequestParam(name = "seatId", required = false) Integer seatIdParam,
	        @RequestParam(name = "admin", required = false, defaultValue = "false") boolean showAll,
	        Model model) {

	    // seatIdはCookie優先、なければURLパラメータから取得
	    Integer seatId = (seatIdCookie != null) ? seatIdCookie : seatIdParam;

	    if (seatId == null) {
	        throw new IllegalArgumentException("seatIdが指定されていません（Cookieにもクエリにも存在しません）");
	    }

		model.addAttribute("seatId", seatId);
		model.addAttribute("storeId", storeId);

		List<MenuWithOptionsDTO> menusWithOptions;
		List<MenuGroup> menuGroups;

		if (showAll) {
			menusWithOptions = menuService.getAllMenusWithOptions(storeId);
			menuGroups = menuAddService.getAdminMenuGroups(storeId);
		} else {
			menusWithOptions = menuService.getMenusWithOptions(storeId);
			menuGroups = menuAddService.getPlanActivatedCustomerMenuGroups(storeId, seatId);
		}

		model.addAttribute("menus", menusWithOptions);
		model.addAttribute("menuGroups", menuGroups);

		return "order";
	}

	/**
	 * 来店登録を行います
	 * Visit および Payment エンティティを作成し、WebSocket通知を送信します
	 * 
	 * @param seatId 座席ID
	 * @param storeId 店舗ID
	 * @param numberOfPeople 来店人数
	 * @param redirectAttributes リダイレクト先に渡す属性
	 * @return リダイレクト先URL
	 */
	@PostMapping
	public String createVisit(@RequestParam("seat.seatId") Integer seatId,
			@RequestParam("store.storeId") Integer storeId,
			@RequestParam("numberOfPeople") Integer numberOfPeople,
			RedirectAttributes redirectAttributes) {

		try {
			orderService.createVisit(seatId, storeId, numberOfPeople);
			redirectAttributes.addFlashAttribute("registerSuccess", true);
			return "redirect:/seats?storeId=" + storeId;
		} catch (Exception e) {
			return "/error";
		}
	}

	/**
	 * 注文を登録します
	 * 
	 * @param items 注文商品のリスト
	 * @param visitId 来店ID（Cookieから取得）
	 * @param storeId 店舗ID（Cookieから取得）
	 * @param request HTTPリクエスト
	 * @return 処理結果のレスポンス
	 */
	@PostMapping("/submit")
	public ResponseEntity<Map<String, String>> submitOrder(@RequestBody List<OrderItemDto> items,
            @CookieValue("visitId") Integer visitId,
            @CookieValue("storeId") Integer storeId,
            HttpServletRequest request) {
        
        Integer userId = cookieUtil.getCookieValueAsInteger(request, "userId");
        
        try {
            orderService.submitOrder(items, visitId, storeId, userId);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            Map<String, String> responseBody = new HashMap<>();
            responseBody.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(responseBody);
        } catch (Exception e) {
            Map<String, String> responseBody = new HashMap<>();
            responseBody.put("message", "注文の処理中にエラーが発生しました。");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(responseBody);
        }
    }

	/**
	 * 注文履歴を取得します
	 * 
	 * @param storeId 店舗ID（Cookieから取得）
	 * @param seatIdCookie 座席ID（Cookieから取得）
	 * @param seatIdParam 座席ID（URLパラメータから取得）
	 * @return 注文履歴のリスト
	 */
	@GetMapping("/history")
	@ResponseBody
	public List<OrderHistoryDto> getOrderHistory(
			@CookieValue(name = "storeId") Integer storeId,
			@CookieValue(name = "seatId", required = false) Integer seatIdCookie,
			@RequestParam(name = "seatId", required = false) Integer seatIdParam) {
	    
		Integer seatId = (seatIdCookie != null) ? seatIdCookie : seatIdParam;
		if (seatId == null) {
	        throw new IllegalArgumentException("seatIdが指定されていません（Cookieにもクエリにも存在しません）");
	    }
		
		return orderService.getOrderHistory(storeId, seatId);
	}

	/**
	 * 注文商品を表すDTO
	 */
	public static class OrderItemDto {
		private Integer menuId;
		private Integer taxRateId;
		private Integer quantity;
		private List<Integer> optionItemIds;

		public Integer getMenuId() {
			return menuId;
		}

		public void setMenuId(Integer menuId) {
			this.menuId = menuId;
		}

		public Integer getTaxRateId() {
			return taxRateId;
		}

		public void setTaxRateId(Integer taxRateId) {
			this.taxRateId = taxRateId;
		}

		public Integer getQuantity() {
			return quantity;
		}

		public void setQuantity(Integer quantity) {
			this.quantity = quantity;
		}

		public List<Integer> getOptionItemIds() {
			return optionItemIds;
		}

		public void setOptionItemIds(List<Integer> optionItemIds) {
			this.optionItemIds = optionItemIds;
		}
	}
}
