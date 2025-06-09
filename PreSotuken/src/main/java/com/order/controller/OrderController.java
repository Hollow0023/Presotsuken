// src/main/java/com/order/controller/OrderController.java (修正版)

package com.order.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap; // HashMapを使うのでimport
import java.util.List;
import java.util.Map; // Mapを使うのでimport
import java.util.stream.Collectors;

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
import org.springframework.web.servlet.mvc.support.RedirectAttributes; // createVisitで使われていたので残す

// ★ 新しくインポートするクラスを追加
import com.order.dto.MenuWithOptionsDTO;
import com.order.dto.OrderHistoryDto;
import com.order.entity.Menu;
import com.order.entity.MenuGroup; // ★ 追加：MenuGroupをインポート
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
import com.order.repository.SeatRepository;
import com.order.repository.StoreRepository;
import com.order.repository.TaxRateRepository;
import com.order.repository.UserRepository;
import com.order.repository.VisitRepository;
import com.order.service.MenuAddService; // ★ 追加：MenuAddServiceをインポート
import com.order.service.MenuService;
import com.order.service.PrintService;

import lombok.RequiredArgsConstructor; // Lombokのアノテーション

@Controller
@RequiredArgsConstructor // finalフィールドのコンストラクタを自動生成
@RequestMapping("/order")
public class OrderController {

	// フィールドインジェクションではなく、コンストラクタインジェクションに統一するために全てfinalにする
	// @Autowired は不要になる

	private final MenuService menuService;
	private final MenuAddService menuAddService; // ★ 追加：MenuAddServiceを注入

	private final MenuRepository menuRepository;
	private final PaymentRepository paymentRepository;
	private final PaymentDetailRepository paymentDetailRepository;
	private final TaxRateRepository taxRateRepository;
	private final VisitRepository visitRepository;
	private final UserRepository userRepository;
	private final PrintService printService; // ★ 追加するリポジトリ

	private final OptionItemRepository optionItemRepository;
	private final PaymentDetailOptionRepository paymentDetailOptionRepository;
	private final StoreRepository storeRepository;
	private final SeatRepository seatRepository;
	private final SimpMessagingTemplate messagingTemplate;

	@GetMapping // 注文画面の表示
	public String showOrderPage(@CookieValue("seatId") Integer seatId,
			@CookieValue("storeId") Integer storeId,
			@RequestParam(name = "admin", required = false, defaultValue = "false") boolean showAll,
			Model model) {

		model.addAttribute("seatId", seatId);
		model.addAttribute("storeId", storeId);

		List<MenuWithOptionsDTO> menusWithOptions;
		List<MenuGroup> menuGroups; // メニューグループのリスト

		// showAll パラメータ（admin=true）によって、メニューとメニューグループの取得ロジックを切り替える
		if (showAll) { // 管理者向け表示の場合
			menusWithOptions = menuService.getAllMenusWithOptions(storeId); // 品切れも表示する
			menuGroups = menuAddService.getAdminMenuGroups(storeId); // ★ 管理者向け（全ての）メニューグループを取得
		} else { // 通常の顧客向け表示の場合
			menusWithOptions = menuService.getMenusWithOptions(storeId); // 品切れは表示しない
			menuGroups = menuAddService.getCustomerMenuGroups(storeId); // ★ 顧客向け（管理者専用でない）メニューグループを取得
		}

		model.addAttribute("menus", menusWithOptions);
		model.addAttribute("menuGroups", menuGroups); // ★ 修正されたmenuGroupsをmodelに追加

		return "order"; // order.html を返す
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
	public ResponseEntity<Void> submitOrder(@RequestBody List<OrderItemDto> items,
			@CookieValue("visitId") Integer visitId,
			@CookieValue("storeId") Integer storeId,
			@CookieValue(name = "userId", required = false) Integer userId) {
		User user = null;
		Payment payment = paymentRepository.findByVisitVisitId(visitId);
		List<PaymentDetail> submitDetails = new ArrayList<>();

		if (userId != null) {
			user = userRepository.findById(userId).orElse(null);
		}
		Integer seatId = visitRepository.findById(visitId)
				.map(Visit::getSeat)
				.map(Seat::getSeatId)
				.orElseThrow(() -> new IllegalArgumentException("無効なvisitId: " + visitId));

		for (OrderItemDto item : items) {
			Menu menu = menuRepository.findById(item.getMenuId())
					.orElseThrow(() -> new RuntimeException("Menu not found with ID: " + item.getMenuId()));
			TaxRate taxRate = taxRateRepository.findById(item.getTaxRateId())
					.orElseThrow(() -> new RuntimeException("TaxRate not found with ID: " + item.getTaxRateId()));

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
			submitDetails.add(savedDetail);

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
			printService.printLabelsForOrder(submitDetails, seatId);
		}

		return ResponseEntity.ok().build();
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
}