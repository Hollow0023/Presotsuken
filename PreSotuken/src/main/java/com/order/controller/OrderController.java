package com.order.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired; // @Autowired は残しておく場合
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.order.dto.MenuWithOptionsDTO;
import com.order.dto.OrderHistoryDto;
import com.order.entity.Menu;
// ★ 新しくインポートするクラスを追加
import com.order.entity.OptionItem;
import com.order.entity.Payment;
import com.order.entity.PaymentDetail;
import com.order.entity.PaymentDetailOption;
import com.order.entity.TaxRate;
import com.order.entity.User;
import com.order.entity.Visit;
import com.order.repository.MenuGroupRepository;
import com.order.repository.MenuRepository;
import com.order.repository.OptionItemRepository;
import com.order.repository.PaymentDetailOptionRepository;
import com.order.repository.PaymentDetailRepository;
import com.order.repository.PaymentRepository;
import com.order.repository.TaxRateRepository;
import com.order.repository.UserRepository;
import com.order.repository.VisitRepository;
import com.order.service.MenuService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/order")
public class OrderController {
	
    // @Autowired は残しておく場合 (LombokのRequiredArgsConstructorと併用するなら、finalにする方が推奨)
    @Autowired
    private MenuService menuService; 

    // final フィールドとして追加
    private final MenuGroupRepository menuGroupRepository;
    private final MenuRepository menuRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentDetailRepository paymentDetailRepository;
    private final TaxRateRepository taxRateRepository;
    private final VisitRepository visitRepository;
    private final UserRepository userRepository;
    
    // ★ 新しく追加するリポジトリ
    private final OptionItemRepository optionItemRepository; 
    private final PaymentDetailOptionRepository paymentDetailOptionRepository; 

    @GetMapping
    public String showOrderPage(@CookieValue("seatId") Integer seatId,
                                @CookieValue("storeId") Integer storeId,
                                @RequestParam(name = "admin", required = false, defaultValue = "false") boolean showAll,
                                Model model) {

        model.addAttribute("seatId", seatId);
        model.addAttribute("storeId", storeId);

        List<MenuWithOptionsDTO> menusWithOptions;
        if (showAll) {
            menusWithOptions = menuService.getAllMenusWithOptions(storeId); // ★ 全表示用メソッド
        } else {
            menusWithOptions = menuService.getMenusWithOptions(storeId); // ★ 時間帯絞り込みあり
        }

        model.addAttribute("menus", menusWithOptions);
        model.addAttribute("menuGroups", menuGroupRepository.findByStore_StoreId(storeId));

        return "order";
    }

    @PostMapping("/submit")
    public ResponseEntity<Void> submitOrder(@RequestBody List<OrderItemDto> items,
                                            @CookieValue("visitId") Integer visitId,
                                            @CookieValue("storeId") Integer storeId,
                                            @CookieValue(name = "userId", required = false) Integer userId) {
    	User user = null;
        Payment payment = paymentRepository.findByVisitVisitId(visitId);
        
        if (userId != null) {
            user = userRepository.findById(userId).orElse(null); // orElseThrow()だと存在しないと落ちるから注意
        }
       
        for (OrderItemDto item : items) {
            Menu menu = menuRepository.findById(item.getMenuId()).orElseThrow();
            TaxRate taxRate = taxRateRepository.findById(item.getTaxRateId()).orElseThrow();
            
            PaymentDetail detail = new PaymentDetail();
            detail.setPayment(payment);
            detail.setStore(menu.getStore());
            detail.setMenu(menu);
            detail.setQuantity(item.getQuantity());
            detail.setUser(user);
            detail.setTaxRate(taxRate);
            detail.setSubtotal((double) (menu.getPrice().intValue() * item.getQuantity()));
            
            // ★ PaymentDetailを保存し、その結果を取得する
            PaymentDetail savedDetail = paymentDetailRepository.save(detail);

            // ★ オプション情報を保存する処理を追加
            if (item.getOptionItemIds() != null && !item.getOptionItemIds().isEmpty()) {
                for (Integer optionItemId : item.getOptionItemIds()) {
                    OptionItem optionItem = optionItemRepository.findById(optionItemId)
                            .orElseThrow(() -> new RuntimeException("OptionItem not found with ID: " + optionItemId));
                    
                    PaymentDetailOption paymentDetailOption = new PaymentDetailOption();
                    paymentDetailOption.setPaymentDetail(savedDetail); // 保存したPaymentDetailのIDを設定
                    paymentDetailOption.setOptionItem(optionItem); // OptionItemを設定
                    
                    paymentDetailOptionRepository.save(paymentDetailOption); // PaymentDetailOptionを保存
                }
            }
        }

        return ResponseEntity.ok().build();
    }

    public static class OrderItemDto {
        private Integer menuId;
        private Integer taxRateId;
        private Integer quantity;
        private List<Integer> optionItemIds; // ★ オプションIDのリストを追加

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
        // ★ optionItemIds の getter/setterを追加
        public List<Integer> getOptionItemIds() {
            return optionItemIds;
        }
        public void setOptionItemIds(List<Integer> optionItemIds) {
            this.optionItemIds = optionItemIds;
        }
    }
    
    @GetMapping("/history")
    @ResponseBody
    public List<OrderHistoryDto> getOrderHistory(
            @CookieValue(name = "storeId") Integer storeId,
            @CookieValue(name = "seatId") Integer seatId
    ) {
        Visit currentVisit = visitRepository.findTopByStore_StoreIdAndSeat_SeatIdOrderByVisitTimeDesc(storeId, seatId);
        if (currentVisit == null) return List.of();

        Payment payment = paymentRepository.findByVisitVisitId(currentVisit.getVisitId());
        if (payment == null) return List.of();

        List<PaymentDetail> details = paymentDetailRepository.findByPaymentPaymentId(payment.getPaymentId());

        return details.stream().map(detail -> {
            OrderHistoryDto dto = new OrderHistoryDto();
            dto.setMenuName(detail.getMenu().getMenuName());
            dto.setQuantity(detail.getQuantity());
            dto.setPrice(detail.getMenu().getPrice());
            dto.setTaxRate(detail.getTaxRate().getRate());

            double subtotal = detail.getMenu().getPrice() * detail.getQuantity();
            dto.setSubtotal((int) Math.round(subtotal * (1 + dto.getTaxRate())));

            return dto;
        }).collect(Collectors.toList());
    }
}