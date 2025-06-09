package com.order.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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

// ★ 新しくインポートするクラスを追加
import com.order.dto.MenuWithOptionsDTO;
import com.order.dto.OrderHistoryDto; // これを修正
import com.order.entity.Menu;
import com.order.entity.OptionItem;
import com.order.entity.Payment;
import com.order.entity.PaymentDetail;
import com.order.entity.PaymentDetailOption; // これを修正
import com.order.entity.Seat;
import com.order.entity.TaxRate;
import com.order.entity.User;
import com.order.entity.Visit;
import com.order.repository.MenuGroupRepository;
import com.order.repository.MenuRepository;
import com.order.repository.OptionItemRepository; // 追加
import com.order.repository.PaymentDetailOptionRepository; // 追加
import com.order.repository.PaymentDetailRepository;
import com.order.repository.PaymentRepository;
import com.order.repository.TaxRateRepository;
import com.order.repository.UserRepository;
import com.order.repository.VisitRepository;
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

    private final MenuGroupRepository menuGroupRepository;
    private final MenuRepository menuRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentDetailRepository paymentDetailRepository;
    private final TaxRateRepository taxRateRepository;
    private final VisitRepository visitRepository;
    private final UserRepository userRepository;
    private final PrintService printService;
    
    // ★ 追加するリポジトリ
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
            menusWithOptions = menuService.getAllMenusWithOptions(storeId);
        } else {
            menusWithOptions = menuService.getMenusWithOptions(storeId);
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
        Payment payment = paymentRepository.findByVisitVisitId(visitId); // 既存のPaymentを取得
        List<PaymentDetail> submitDetails = new ArrayList<>();
        
        if (userId != null) {
            user = userRepository.findById(userId).orElse(null); // ユーザーが存在しない場合はnull
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
            // サブトータルの計算: 税抜き価格 * 数量
            detail.setSubtotal((double) (menu.getPrice() * item.getQuantity())); 
            detail.setOrderTime(LocalDateTime.now());
            
            // ★ PaymentDetailを保存し、その結果（IDが付与されたエンティティ）を取得
            PaymentDetail savedDetail = paymentDetailRepository.save(detail);
            submitDetails.add(savedDetail);

            // ★ オプション情報を保存する処理
            if (item.getOptionItemIds() != null && !item.getOptionItemIds().isEmpty()) {
                for (Integer optionItemId : item.getOptionItemIds()) {
                    OptionItem optionItem = optionItemRepository.findById(optionItemId)
                            .orElseThrow(() -> new RuntimeException("OptionItem not found with ID: " + optionItemId));
                    
                    PaymentDetailOption paymentDetailOption = new PaymentDetailOption();
                    // PaymentDetailとOptionItemのオブジェクトを直接設定 (エンティティが参照を持つ形式のため)
                    paymentDetailOption.setPaymentDetail(savedDetail); 
                    paymentDetailOption.setOptionItem(optionItem); 
                    
                    paymentDetailOptionRepository.save(paymentDetailOption);
                }
            }
            printService.printLabelsForOrder(submitDetails, seatId);
        }

        return ResponseEntity.ok().build();
    }

    // クライアントからの注文データを受け取るための内部クラス
    public static class OrderItemDto {
        private Integer menuId;
        private Integer taxRateId;
        private Integer quantity;
        private List<Integer> optionItemIds; // ★追加：選択されたオプションのIDリスト

        // GetterとSetter (Lombokで@Dataを使うなら不要だが、ここでは明示的に記述)
        public Integer getMenuId() { return menuId; }
        public void setMenuId(Integer menuId) { this.menuId = menuId; }
        public Integer getTaxRateId() { return taxRateId; }
        public void setTaxRateId(Integer taxRateId) { this.taxRateId = taxRateId; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public List<Integer> getOptionItemIds() { return optionItemIds; }
        public void setOptionItemIds(List<Integer> optionItemIds) { this.optionItemIds = optionItemIds; }
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
            dto.setPrice(detail.getMenu().getPrice()); // 税抜き単価
            dto.setTaxRate(detail.getTaxRate().getRate()); // 税率

            // 税込み小計を計算してDTOにセット
            double subtotalWithTax = detail.getMenu().getPrice() * detail.getQuantity() * (1 + detail.getTaxRate().getRate());
            dto.setSubtotal((int) Math.round(subtotalWithTax)); // 税込合計

            // ★ 選択されたオプションの名前を取得し、DTOに設定
            List<String> optionNames = paymentDetailOptionRepository.findByPaymentDetail(detail).stream()
                                            // pdo.getOptionItem().getItemName() に変更
                                            .map(pdo -> pdo.getOptionItem().getItemName()) 
                                            .collect(Collectors.toList());
            dto.setSelectedOptionNames(optionNames);

            return dto;
        }).collect(Collectors.toList());
    }
}