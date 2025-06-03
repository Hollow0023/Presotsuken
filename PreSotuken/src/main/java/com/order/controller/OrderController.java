package com.order.controller;

import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.order.dto.OrderHistoryDto;
import com.order.entity.Menu;
import com.order.entity.Payment;
import com.order.entity.PaymentDetail;
import com.order.entity.TaxRate;
import com.order.entity.User;
import com.order.entity.Visit;
import com.order.repository.MenuGroupRepository;
import com.order.repository.MenuRepository;
import com.order.repository.PaymentDetailRepository;
import com.order.repository.PaymentRepository;
import com.order.repository.TaxRateRepository;
import com.order.repository.UserRepository;
import com.order.repository.VisitRepository;

@Controller
@RequestMapping("/order")
public class OrderController {

    private final MenuGroupRepository menuGroupRepository;
    private final MenuRepository menuRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentDetailRepository paymentDetailRepository;
    private final TaxRateRepository taxRateRepository;
    private final VisitRepository visitRepository;
    private final UserRepository userRepository;

    @Autowired
    public OrderController(MenuGroupRepository menuGroupRepository,
                           MenuRepository menuRepository,
                           PaymentRepository paymentRepository,
                           PaymentDetailRepository paymentDetailRepository,
                           TaxRateRepository taxRateRepository,
                           VisitRepository visitRepository,
                           UserRepository userRepository) 
    	{
    	this.visitRepository = visitRepository;
    	this.menuGroupRepository = menuGroupRepository;
        this.menuRepository = menuRepository;
        this.paymentRepository = paymentRepository;
        this.paymentDetailRepository = paymentDetailRepository;
        this.taxRateRepository = taxRateRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("")
    public String showOrderPage(@CookieValue("seatId") Integer seatId,
                                @CookieValue("storeId") Integer storeId,
                                Model model) {
    	
        LocalTime now = LocalTime.now();
        List<Menu> menus = menuRepository.findMenusAvailableAt(now);
    	
        model.addAttribute("seatId", seatId);
        model.addAttribute("storeId", storeId);

        var menuGroups = menuGroupRepository.findByStore_StoreId(storeId);


        model.addAttribute("menuGroups", menuGroups);
        model.addAttribute("menus", menus);

        return "order";
    }

    @PostMapping("/submit")
    public ResponseEntity<Void> submitOrder(@RequestBody List<OrderItemDto> items,
                                            @CookieValue("visitId") Integer visitId,
                                            @CookieValue("storeId") Integer storeId,
                                            @CookieValue("userId") Integer userId) {
        Payment payment = paymentRepository.findByVisitVisitId(visitId);
        User user = userRepository.findById(userId).orElseThrow();

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
            paymentDetailRepository.save(detail);
        }

        return ResponseEntity.ok().build();
    }

    public static class OrderItemDto {
        private Integer menuId;
        private Integer taxRateId;
        private Integer quantity;

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
            dto.setSubtotal(detail.getSubtotal().intValue());
            return dto;
        }).collect(Collectors.toList());
    }

}
