package com.order.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.order.controller.OrderController.OrderItemDto;
import com.order.dto.OrderHistoryDto;
import com.order.entity.Menu;
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
import com.order.repository.PlanMenuGroupMapRepository;
import com.order.repository.SeatRepository;
import com.order.repository.StoreRepository;
import com.order.repository.TaxRateRepository;
import com.order.repository.UserRepository;
import com.order.repository.VisitRepository;

import lombok.RequiredArgsConstructor;

/**
 * 注文処理に関するビジネスロジックを提供するサービス
 */
@Service
@RequiredArgsConstructor
public class OrderService {

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
    private final PlanMenuGroupMapRepository planMenuGroupMapRepository;

    /**
     * 来店登録を行います
     * Visit および Payment エンティティを作成し、WebSocket通知を送信します
     * 
     * @param seatId 座席ID
     * @param storeId 店舗ID
     * @param numberOfPeople 来店人数
     * @throws RuntimeException 店舗または座席が見つからない場合
     */
    @Transactional
    public void createVisit(Integer seatId, Integer storeId, Integer numberOfPeople) {
        // 店舗・座席を取得
        Store store = storeRepository.findById(storeId)
            .orElseThrow(() -> new RuntimeException("指定された店舗が見つかりません: " + storeId));
        Seat seat = seatRepository.findById(seatId)
            .orElseThrow(() -> new RuntimeException("指定された座席が見つかりません: " + seatId));

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
            
            messagingTemplate.convertAndSend("/topic/seats/" + seatId, payload);
        } else {
            throw new RuntimeException("来店登録に失敗しました");
        }
    }

    /**
     * 注文を登録します
     * 
     * @param items 注文商品のリスト
     * @param visitId 来店ID
     * @param storeId 店舗ID
     * @param userId ユーザーID（オプション）
     * @throws IllegalArgumentException 品切れ商品が含まれている場合や無効なIDが指定された場合
     */
    @Transactional
    public void submitOrder(List<OrderItemDto> items, Integer visitId, Integer storeId, Integer userId) {
        User user = null;
        if (userId != null) {
            user = userRepository.findById(userId).orElse(null);
        }

        Payment payment = paymentRepository.findByVisitVisitId(visitId);
        if (payment == null) {
            throw new IllegalArgumentException("無効なvisitId: " + visitId);
        }

        List<PaymentDetail> submitDetails = new ArrayList<>();
        
        // submitOrderの開始時にseatIdを確定しておく
        Integer seatId = visitRepository.findById(visitId)
                .map(Visit::getSeat)
                .map(Seat::getSeatId)
                .orElseThrow(() -> new IllegalArgumentException("無効なvisitId: " + visitId));

        for (OrderItemDto item : items) {
            Menu menu = menuRepository.findById(item.getMenuId())
                    .orElseThrow(() -> new RuntimeException("Menu not found with ID: " + item.getMenuId()));
            
            // 削除済みメニューのチェック
            if (menu.getDeletedAt() != null) {
                throw new IllegalArgumentException("削除されたメニュー「" + menu.getMenuName() + "」が含まれていました。再度注文をお願いします。");
            }
            
            TaxRate taxRate = taxRateRepository.findById(item.getTaxRateId())
                    .orElseThrow(() -> new RuntimeException("TaxRate not found with ID: " + item.getTaxRateId()));
            
            // 品切れチェック
            if (Boolean.TRUE.equals(menu.getIsSoldOut())) {
                throw new IllegalArgumentException("品切れ商品「" + menu.getMenuName() + "」が含まれていました。再度注文をお願いします。");
            }

            PaymentDetail detail = createPaymentDetail(payment, menu, taxRate, item.getQuantity(), user);
            PaymentDetail savedDetail = paymentDetailRepository.save(detail);
            submitDetails.add(savedDetail);

            // オプション商品の処理
            if (item.getOptionItemIds() != null && !item.getOptionItemIds().isEmpty()) {
                processOptionItems(savedDetail, item.getOptionItemIds());
            }
            
            // 単品伝票の印刷
            printService.printLabelsForOrder(savedDetail, seatId);

            // 飲み放題開始メニューの処理
            if (Boolean.TRUE.equals(menu.getIsPlanStarter())) {
                processPlanStarterMenu(menu, payment, seatId);
            }
        }

        // 小計伝票の印刷
        printService.printReceiptForPayment(submitDetails, seatId, storeId); 
    }

    /**
     * 注文履歴を取得します
     * 
     * @param storeId 店舗ID
     * @param seatId 座席ID
     * @return 注文履歴のリスト
     */
    public List<OrderHistoryDto> getOrderHistory(Integer storeId, Integer seatId) {
        Visit currentVisit = visitRepository.findTopByStore_StoreIdAndSeat_SeatIdOrderByVisitTimeDesc(storeId, seatId);
        if (currentVisit == null) {
            return List.of();
        }

        Payment payment = paymentRepository.findByVisitVisitId(currentVisit.getVisitId());
        if (payment == null) {
            return List.of();
        }

        List<PaymentDetail> details = paymentDetailRepository.findByPaymentPaymentId(payment.getPaymentId());

        return details.stream().map(this::convertToOrderHistoryDto).collect(Collectors.toList());
    }

    /**
     * PaymentDetailエンティティを作成します
     */
    private PaymentDetail createPaymentDetail(Payment payment, Menu menu, TaxRate taxRate, Integer quantity, User user) {
        PaymentDetail detail = new PaymentDetail();
        detail.setPayment(payment);
        detail.setStore(menu.getStore());
        detail.setMenu(menu);
        detail.setQuantity(quantity);
        detail.setUser(user);
        detail.setTaxRate(taxRate);
        detail.setSubtotal((double) (menu.getPrice() * quantity));
        detail.setOrderTime(LocalDateTime.now());
        return detail;
    }

    /**
     * オプション商品を処理します
     */
    private void processOptionItems(PaymentDetail paymentDetail, List<Integer> optionItemIds) {
        for (Integer optionItemId : optionItemIds) {
            OptionItem optionItem = optionItemRepository.findById(optionItemId)
                    .orElseThrow(() -> new RuntimeException("OptionItem not found with ID: " + optionItemId));

            PaymentDetailOption paymentDetailOption = new PaymentDetailOption();
            paymentDetailOption.setPaymentDetail(paymentDetail);
            paymentDetailOption.setOptionItem(optionItem);

            paymentDetailOptionRepository.save(paymentDetailOption);
        }
    }

    /**
     * 飲み放題開始メニューの処理を行います
     */
    private void processPlanStarterMenu(Menu menu, Payment payment, Integer seatId) {
        Integer planId = menu.getPlanId();
        List<PaymentDetail> activePlans = paymentDetailRepository.findByPaymentPaymentIdAndMenuIsPlanStarterTrue(payment.getPaymentId());
        
        // 現在有効な全てのisPlanStarterメニューのplanIdを収集
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
        
        // 重複を排除
        allActivatedMenuGroupIds = allActivatedMenuGroupIds.stream().distinct().collect(Collectors.toList());

        // WebSocket通知を送信
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "PLAN_ACTIVATED");
        payload.put("seatId", seatId);
        payload.put("planId", planId);
        payload.put("activatedMenuGroupIds", allActivatedMenuGroupIds);

        messagingTemplate.convertAndSend("/topic/seats/" + seatId, payload);
        System.out.println("WebSocket通知: seatId " + seatId + " でプラン " + planId + " がアクティブ化されました。");
    }

    /**
     * PaymentDetailを注文履歴DTOに変換します
     */
    private OrderHistoryDto convertToOrderHistoryDto(PaymentDetail detail) {
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
    }
}