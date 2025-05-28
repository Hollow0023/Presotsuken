package com.order.controller;

import java.time.LocalDateTime;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.order.entity.Payment;
import com.order.entity.Seat;
import com.order.entity.Store;
import com.order.entity.Visit;
import com.order.repository.PaymentRepository;
import com.order.repository.SeatRepository;
import com.order.repository.StoreRepository;
import com.order.repository.VisitRepository;

@Controller
@RequestMapping("/visits")
public class VisitController {

    private final VisitRepository visitRepository;
    private final PaymentRepository paymentRepository;
    private final StoreRepository storeRepository;
    private final SeatRepository seatRepository;

    public VisitController(VisitRepository visitRepository,
                           PaymentRepository paymentRepository,
                           StoreRepository storeRepository,
                           SeatRepository seatRepository) {
        this.visitRepository = visitRepository;
        this.paymentRepository = paymentRepository;
        this.storeRepository = storeRepository;
        this.seatRepository = seatRepository;
    }

    @PostMapping
    public String createVisit(@RequestParam("seat.seatId") Integer seatId,
                               @RequestParam("store.storeId") Integer storeId,
                               @RequestParam("numberOfPeople") Integer numberOfPeople,RedirectAttributes redirectAttributes) {
    	
        // 店舗・座席を取得
        Store store = storeRepository.findById(storeId).orElseThrow();
        Seat seat = seatRepository.findById(seatId).orElseThrow();
        
        
        //登録前に同一席に退出前のレコードがないか確認

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
        Payment savedPayment =  paymentRepository.save(payment);
        
        
        
        //登録成否
        boolean success = false;
        if (savedVisit.getVisitId() != null && savedPayment.getPaymentId() != null) {
        	success = true;
        	redirectAttributes.addFlashAttribute("registerSuccess", success);
            return "redirect:/seats?storeId=" + storeId;
        } else {
        	return "/error";
        }

        
    }
}
