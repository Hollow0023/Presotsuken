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
import com.order.entity.OptionItem;
import com.order.entity.Payment;
import com.order.entity.PaymentDetail;
import com.order.entity.PaymentDetailOption;
import com.order.entity.Seat;
import com.order.entity.Store;
import com.order.entity.TaxRate;
import com.order.entity.User;
import com.order.entity.Visit;
import com.order.repository.MenuRepository;
import com.order.repository.OptionItemRepository;
import com.order.repository.PaymentDetailOptionRepository;
import com.order.repository.PaymentDetailRepository;
import com.order.repository.PaymentRepository;
import com.order.repository.PlanMenuGroupMapRepository; // PlanMenuGroupMapRepositoryを追加
// ★ 新しくインポートするリポジトリ
import com.order.repository.PlanRepository; // PlanRepositoryを追加 - 今回のロジックでは直接使わないけど、注入自体はOK
import com.order.repository.SeatRepository;
import com.order.repository.StoreRepository;
import com.order.repository.TaxRateRepository;
import com.order.repository.UserRepository;
import com.order.repository.VisitRepository;
import com.order.service.MenuAddService;
import com.order.service.MenuService;
import com.order.service.PrintService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/order")
public class OrderController {

	private final MenuService menuService;
	private final MenuAddService menuAddService;

	private final MenuRepository menuRepository;
	private final PaymentRepository paymentRepository;
	private final PaymentDetailRepository paymentDetailRepository;
	private final TaxRateRepository taxRateRepository;
	private final VisitRepository visitRepository;
	private final UserRepository userRepository;
	private final PrintService printService;

	private final OptionItemRepository optionItemRepository;
	private final PaymentDetailOptionRepository paymentDetailOptionRepository;
	private final StoreRepository storeRepository;
	private final SeatRepository seatRepository;
	private final SimpMessagingTemplate messagingTemplate;

	// ★ 新しく注入するリポジトリ
	private final PlanRepository planRepository; // PlanRepositoryはOrderControllerでは直接使用しないが、依存関係としては問題なし
	private final PlanMenuGroupMapRepository planMenuGroupMapRepository;
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

//	@GetMapping
//	public String showOrderPage(
//			@CookieValue("seatId") Integer seatId,
//			@CookieValue("storeId") Integer storeId,
//			@RequestParam(name = "admin", required = false, defaultValue = "false") boolean showAll,
//			Model model) {

		model.addAttribute("seatId", seatId);
		model.addAttribute("storeId", storeId);

		List<MenuWithOptionsDTO> menusWithOptions;
		List<MenuGroup> menuGroups;

		if (showAll) {
			menusWithOptions = menuService.getAllMenusWithOptions(storeId);
			// ★ 管理者向けはisPlanTargetに関わらず全て表示
			menuGroups = menuAddService.getAdminMenuGroups(storeId);
		} else {
			menusWithOptions = menuService.getMenusWithOptions(storeId);
			
			// ★ ここが一番の変更点！飲み放題がアクティブかどうかに応じてメニューグループを調整する
			// Service層のメソッドを呼び出すことで、ロジックがControllerに直接書かれるのを避ける
			menuGroups = menuAddService.getPlanActivatedCustomerMenuGroups(storeId, seatId);
			// ※ isPlanActiveForSeat の直接呼び出しは不要になる。
			// menuAddService.getPlanActivatedCustomerMenuGroups 内で
			// 飲み放題の状況を判断して適切なMenuGroupリストを返すため。
		}

		model.addAttribute("menus", menusWithOptions);
		model.addAttribute("menuGroups", menuGroups);

		return "order";
	}

	// createVisitメソッド (変更なし)
	@PostMapping
	public String createVisit(@RequestParam("seat.seatId") Integer seatId,
			@RequestParam("store.storeId") Integer storeId,
			@RequestParam("numberOfPeople") Integer numberOfPeople,
			RedirectAttributes redirectAttributes) {

		// 店舗・座席を取得
		Store store = storeRepository.findById(storeId).orElseThrow();
		Seat seat = seatRepository.findById(seatId).orElseThrow();

		// Visit 登録
		Visit visit = new Visit();
		visit.setStore(store);
		visit.setSeat(seat);
		visit.setNumberOfPeople(numberOfPeople);
		visit.setVisitTime(LocalDateTime.now());
		Visit savedVisit = visitRepository.save(visit);

		// Payment 登録
		Payment payment = new Payment();
		payment.setStore(store);
		payment.setVisit(visit);
		Payment savedPayment = paymentRepository.save(payment);

		// WebSocket 通知
		if (savedVisit.getVisitId() != null && savedPayment.getPaymentId() != null) {
			Map<String, Object> payload = new HashMap<>();
			payload.put("visitId", savedVisit.getVisitId());
			payload.put("storeId", storeId);
			payload.put("seatId", seatId);
			payload.put("userId", null);
			// WebSocket 通知は SimpMessagingTemplate を使って行う
			messagingTemplate.convertAndSend("/topic/seats/" + seatId, payload);

			redirectAttributes.addFlashAttribute("registerSuccess", true);
			return "redirect:/seats?storeId=" + storeId;
		} else {
			return "/error";
		}
	}

	// submitOrderメソッド (変更なし)

	@PostMapping("/submit")
	public ResponseEntity<Map<String, String>> submitOrder(@RequestBody List<OrderItemDto> items,
            @CookieValue("visitId") Integer visitId,
            @CookieValue("storeId") Integer storeId,
            HttpServletRequest request) {
        User user = null;
        Payment payment = paymentRepository.findByVisitVisitId(visitId);
        List<PaymentDetail> submitDetails = new ArrayList<>(); // 今回の注文で追加されたPaymentDetailを収集
        Map<String, String> responseBody = new HashMap<>();
        
        Integer userId = getCookieValueAsInteger(request,"userId");

        if (userId != null) {
            user = userRepository.findById(userId).orElse(null);
        }
        
        // submitOrderの開始時にseatIdを確定しておく
        Integer seatId = visitRepository.findById(visitId)
                .map(Visit::getSeat)
                .map(Seat::getSeatId)
                .orElseThrow(() -> new IllegalArgumentException("無効なvisitId: " + visitId));


        for (OrderItemDto item : items) {
            Menu menu = menuRepository.findById(item.getMenuId())
                    .orElseThrow(() -> new RuntimeException("Menu not found with ID: " + item.getMenuId()));
            TaxRate taxRate = taxRateRepository.findById(item.getTaxRateId())
                    .orElseThrow(() -> new RuntimeException("TaxRate not found with ID: " + item.getTaxRateId()));
            
            if (Boolean.TRUE.equals(menu.getIsSoldOut())) {
                System.err.println("品切れ商品が含まれています: " + menu.getMenuName());
                responseBody.put("message", "品切れ商品「" + menu.getMenuName() + "」が含まれていました。再度注文をお願いします。");
                return ResponseEntity.status(HttpStatus.CONFLICT) // 409 Conflict
                                     .body(responseBody); // エラーメッセージをボディに含める
            }

            PaymentDetail detail = new PaymentDetail();
            detail.setPayment(payment);
            detail.setStore(menu.getStore());
            detail.setMenu(menu);
            detail.setQuantity(item.getQuantity());
            detail.setUser(user);
            detail.setTaxRate(taxRate);
            detail.setSubtotal((double) (menu.getPrice() * item.getQuantity()));
            detail.setOrderTime(LocalDateTime.now());

            PaymentDetail savedDetail = paymentDetailRepository.save(detail);
            submitDetails.add(savedDetail); // 今回の注文で追加されたPaymentDetailをリストに追加

            if (item.getOptionItemIds() != null && !item.getOptionItemIds().isEmpty()) {
                for (Integer optionItemId : item.getOptionItemIds()) {
                    OptionItem optionItem = optionItemRepository.findById(optionItemId)
                            .orElseThrow(() -> new RuntimeException("OptionItem not found with ID: " + optionItemId));

                    PaymentDetailOption paymentDetailOption = new PaymentDetailOption();
                    paymentDetailOption.setPaymentDetail(savedDetail);
                    paymentDetailOption.setOptionItem(optionItem);

                    paymentDetailOptionRepository.save(paymentDetailOption);
                }
            }
            
            // ★★★ 単品伝票の印刷（savedDetailのみをリストにして渡すように変更）★★★
            // これで、各商品が1枚の単品伝票として印刷される
            printService.printLabelsForOrder(List.of(savedDetail), seatId); 
            
            // ★ここからが追加ロジック！飲み放題開始メニューの注文を検知
            // menuエンティティのisPlanStarterがBoolean型なので、NullPointerExceptionを避けるためにequalsを使用
            if (Boolean.TRUE.equals(menu.getIsPlanStarter())) {
                Integer planId = menu.getPlanId(); // 紐づいているplan_idを取得
                List<PaymentDetail> activePlans = paymentDetailRepository.findByPaymentPaymentIdAndMenuIsPlanStarterTrue(payment.getPaymentId());
                
                // activePlansから、現在有効な全てのisPlanStarterメニューのplanIdを収集
                Set<Integer> activePlanIds = activePlans.stream()
                    .map(pd -> pd.getMenu().getPlanId())
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toSet());

                // 全ての有効なplanIdに紐づくmenuGroupのIDを収集
                List<Integer> allActivatedMenuGroupIds = new ArrayList<>();
                for (Integer activePlanId : activePlanIds) {
                    List<Integer> groupIdsForPlan = planMenuGroupMapRepository.findByPlanId(activePlanId).stream()
                        .map(map -> map.getMenuGroupId())
                        .collect(Collectors.toList());
                    allActivatedMenuGroupIds.addAll(groupIdsForPlan);
                }
                //重複を排除してユニークなIDリストにする
                allActivatedMenuGroupIds = allActivatedMenuGroupIds.stream().distinct().collect(Collectors.toList());

                // WebSocketで特定のseatIdのクライアントに通知を送信
                Map<String, Object> payload = new HashMap<>();
                payload.put("type", "PLAN_ACTIVATED");
                payload.put("seatId", seatId);
                payload.put("planId", planId);
                
                payload.put("activatedMenuGroupIds", allActivatedMenuGroupIds);

                messagingTemplate.convertAndSend("/topic/seats/" + seatId, payload);
                System.out.println("WebSocket通知: seatId " + seatId + " でプラン " + planId + " がアクティブ化されました。");
            }
        }

        // ★★★ ループの最後に小計伝票を印刷するメソッドを呼び出す ★★★
        // 今回注文された全てのPaymentDetailをまとめて渡す
        printService.printReceiptForPayment(submitDetails, seatId); 

        return ResponseEntity.ok().build();
    }
// @PostMapping("/submit")
//	public ResponseEntity<Void> submitOrder(@RequestBody List<OrderItemDto> items,
//			@CookieValue("visitId") Integer visitId,
//			@CookieValue("storeId") Integer storeId, // これは今回は直接使わないけど、引数として残す
//			HttpServletRequest request) {
//		User user = null;
//		Payment payment = paymentRepository.findByVisitVisitId(visitId);
//		List<PaymentDetail> submitDetails = new ArrayList<>();
//		
//		Integer userId = getCookieValueAsInteger(request,"userId");
//
//		if (userId != null) {
//			user = userRepository.findById(userId).orElse(null);
//		}
//		
//		// submitOrderの開始時にseatIdを確定しておく
//		Integer seatId = visitRepository.findById(visitId)
//				.map(Visit::getSeat)
//				.map(Seat::getSeatId)
//				.orElseThrow(() -> new IllegalArgumentException("無効なvisitId: " + visitId));
//
//
//		for (OrderItemDto item : items) {
//			Menu menu = menuRepository.findById(item.getMenuId())
//					.orElseThrow(() -> new RuntimeException("Menu not found with ID: " + item.getMenuId()));
//			TaxRate taxRate = taxRateRepository.findById(item.getTaxRateId())
//					.orElseThrow(() -> new RuntimeException("TaxRate not found with ID: " + item.getTaxRateId()));
//
//			PaymentDetail detail = new PaymentDetail();
//			detail.setPayment(payment);
//			detail.setStore(menu.getStore());
//			detail.setMenu(menu);
//			detail.setQuantity(item.getQuantity());
//			detail.setUser(user);
//			detail.setTaxRate(taxRate);
//			detail.setSubtotal((double) (menu.getPrice() * item.getQuantity()));
//			detail.setOrderTime(LocalDateTime.now());
//
//			PaymentDetail savedDetail = paymentDetailRepository.save(detail);
//			submitDetails.add(savedDetail);
//
//			if (item.getOptionItemIds() != null && !item.getOptionItemIds().isEmpty()) {
//				for (Integer optionItemId : item.getOptionItemIds()) {
//					OptionItem optionItem = optionItemRepository.findById(optionItemId)
//							.orElseThrow(() -> new RuntimeException("OptionItem not found with ID: " + optionItemId));
//
//					PaymentDetailOption paymentDetailOption = new PaymentDetailOption();
//					paymentDetailOption.setPaymentDetail(savedDetail);
//					paymentDetailOption.setOptionItem(optionItem);
//
//					paymentDetailOptionRepository.save(paymentDetailOption);
//				}
//			}
//			printService.printLabelsForOrder(submitDetails, seatId);
//			
//			
//			
//			
//			
//			
//
//			// ★ここからが追加ロジック！飲み放題開始メニューの注文を検知
//			// menuエンティティのisPlanStarterがBoolean型なので、NullPointerExceptionを避けるためにequalsを使用
//			if (Boolean.TRUE.equals(menu.getIsPlanStarter())) {
//				Integer planId = menu.getPlanId(); // 紐づいているplan_idを取得
//				List<PaymentDetail> activePlans = paymentDetailRepository.findByPaymentPaymentIdAndMenuIsPlanStarterTrue(payment.getPaymentId());
//				
//				// activePlansから、現在有効な全てのisPlanStarterメニューのplanIdを収集
//				Set<Integer> activePlanIds = activePlans.stream()
//				    .map(pd -> pd.getMenu().getPlanId())
//				    .filter(java.util.Objects::nonNull) // planIdがnullでないことを確認
//				    .collect(java.util.stream.Collectors.toSet()); // 重複を除いてSetにする
//
//				// 全ての有効なplanIdに紐づくmenuGroupのIDを収集
//				List<Integer> allActivatedMenuGroupIds = new ArrayList<>();
//				for (Integer activePlanId : activePlanIds) {
//				    List<Integer> groupIdsForPlan = planMenuGroupMapRepository.findByPlanId(activePlanId).stream()
//				        .map(map -> map.getMenuGroupId())
//				        .collect(Collectors.toList());
//				    allActivatedMenuGroupIds.addAll(groupIdsForPlan);
//				}
//				//重複を排除してユニークなIDリストにする
//				allActivatedMenuGroupIds = allActivatedMenuGroupIds.stream().distinct().collect(Collectors.toList());
//
//				// WebSocketで特定のseatIdのクライアントに通知を送信
//				// `/topic/seats/{seatId}` は入店時通知と同じエンドポイントを使う
//				Map<String, Object> payload = new HashMap<>();
//				payload.put("type", "PLAN_ACTIVATED"); // 通知の種類を明確にする
//				payload.put("seatId", seatId);
//				payload.put("planId", planId); // どのプランが有効になったか
//				
//				payload.put("activatedMenuGroupIds", allActivatedMenuGroupIds);
//
//				messagingTemplate.convertAndSend("/topic/seats/" + seatId, payload);
//				System.out.println("WebSocket通知: seatId " + seatId + " でプラン " + planId + " がアクティブ化されました。");
//			}
//		}
//
//		return ResponseEntity.ok().build();
//	}


	// ★ showOrderPage で使うヘルパーメソッド (Service層に切り出すのが理想)
	// このメソッドは、現在のシート（テーブル）で飲み放題がアクティブかどうかをチェックする
	// ※ OrderControllerからはMenuAddService.getPlanActivatedCustomerMenuGroupsを呼び出すので、
	//    このcheckActivePlanForSeatメソッドはOrderControllerでは直接は使われない。
	//    MenuAddService内のgetActivePlanIdForSeatにロジックが移されているため、
	//    OrderControllerからはこのメソッドは削除してOK。
	private boolean checkActivePlanForSeat(Integer seatId, Integer storeId) {
		// 最新のVisitを取得
		Visit currentVisit = visitRepository.findTopByStore_StoreIdAndSeat_SeatIdOrderByVisitTimeDesc(storeId, seatId);
		if (currentVisit == null) {
			return false; // Visitがないなら、飲み放題もアクティブではない
		}

		// そのVisitに紐づくPaymentを取得
		Payment payment = paymentRepository.findByVisitVisitId(currentVisit.getVisitId());
		if (payment == null) {
			return false; // Paymentがないなら、飲み放題もアクティブではない
		}

		// そのPaymentに紐づくPaymentDetailの中から、is_plan_starterがtrueのメニューがあるか検索
		List<PaymentDetail> planStarterOrders = paymentDetailRepository.findByPaymentPaymentIdAndMenuIsPlanStarterTrue(payment.getPaymentId());

		// 飲み放題開始メニューの注文が1つでもあれば、trueを返す
		return !planStarterOrders.isEmpty();
	}

	// OrderItemDto (変更なし)
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

	// getOrderHistoryメソッド (変更なし)
	@GetMapping("/history")
	@ResponseBody
	public List<OrderHistoryDto> getOrderHistory(
			@CookieValue(name = "storeId") Integer storeId,
			@CookieValue(name = "seatId") Integer seatId) {
		Visit currentVisit = visitRepository.findTopByStore_StoreIdAndSeat_SeatIdOrderByVisitTimeDesc(storeId, seatId);
		if (currentVisit == null)
			return List.of();

		Payment payment = paymentRepository.findByVisitVisitId(currentVisit.getVisitId());
		if (payment == null)
			return List.of();

		List<PaymentDetail> details = paymentDetailRepository.findByPaymentPaymentId(payment.getPaymentId());

		return details.stream().map(detail -> {
			OrderHistoryDto dto = new OrderHistoryDto();
			dto.setMenuName(detail.getMenu().getMenuName());
			dto.setQuantity(detail.getQuantity());
			dto.setPrice(detail.getMenu().getPrice());
			dto.setTaxRate(detail.getTaxRate().getRate());

			double subtotalWithTax = detail.getMenu().getPrice() * detail.getQuantity()
					* (1 + detail.getTaxRate().getRate());
			dto.setSubtotal((int) Math.round(subtotalWithTax));

			List<String> optionNames = paymentDetailOptionRepository.findByPaymentDetail(detail).stream()
					.map(pdo -> pdo.getOptionItem().getItemName())
					.collect(Collectors.toList());
			dto.setSelectedOptionNames(optionNames);

			return dto;
		}).collect(Collectors.toList());
	}
	
	// これをOrderControllerのprivateメソッドとして定義
    private Integer getCookieValueAsInteger(HttpServletRequest request, String cookieName) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (cookieName.equals(cookie.getName())) {
                    String value = cookie.getValue();
                    if (value == null || value.isEmpty() || value.equalsIgnoreCase("null") || value.equalsIgnoreCase("undefined")) {
                        return null;
                    }
                    try {
                        return Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        System.err.println("Warning: 無効なCookie値がIntegerに変換できませんでした (" + cookieName + "): " + value);
                        return null;
                    }
                }
            }
        }
        return null;
    }
}