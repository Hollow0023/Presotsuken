package com.order.controller;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.order.entity.SeatGroup;
import com.order.entity.User;
import com.order.entity.Visit;
import com.order.repository.SeatGroupRepository;
import com.order.repository.SeatRepository;
import com.order.repository.UserRepository;
import com.order.repository.VisitRepository;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

@Controller
@RequestMapping("/seats")
public class SeatController {

    private final SeatGroupRepository seatGroupRepository;
    private final SeatRepository seatRepository;
    private final VisitRepository visitRepository;
    private final UserRepository userRepository;


    public SeatController(SeatGroupRepository seatGroupRepository,
            SeatRepository seatRepository,
            VisitRepository visitRepository,
            UserRepository userRepository) {
		this.seatGroupRepository = seatGroupRepository;
		this.seatRepository = seatRepository;
		this.visitRepository = visitRepository;
		this.userRepository = userRepository;
	}


    @GetMapping
    public String showSeatsByGroup(HttpServletRequest request, Model model) {
        Integer storeId = null;

        // クッキーからstoreIdを探す
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("storeId".equals(cookie.getName())) {
                    try {
                        storeId = Integer.parseInt(cookie.getValue());
                    } catch (NumberFormatException e) {
                        // 値が不正な場合は無視
                    }
                    break;
                }
            }
        }

        if (storeId == null) {
            return "redirect:/"; // ログイン画面へ
        }
        
        
        List<SeatGroup> groups = seatGroupRepository.findByStore_StoreId(storeId);

        Map<SeatGroup, List<com.order.entity.Seat>> groupedSeats = new LinkedHashMap<>();
        for (SeatGroup group : groups) {
            List<com.order.entity.Seat> seats = seatRepository.findByStore_StoreIdAndSeatGroup_SeatGroupId(storeId, group.getSeatGroupId());
            groupedSeats.put(group, seats);
        }
        
        Map<Integer, Visit> visitMap = new HashMap<>();
    	List<Visit> visits = visitRepository.findByStore_StoreIdAndLeaveTimeIsNull(storeId);

    	if (visits != null) {
    	    visitMap = visits.stream()
    	        .collect(Collectors.toMap(v -> v.getSeat().getSeatId(), Function.identity()));
    	}
    	
    	List<User> users = userRepository.findByStore_StoreId(storeId);
    	model.addAttribute("users", users);


    	model.addAttribute("visitMap", visitMap);
        model.addAttribute("activeVisits", visits);
        model.addAttribute("registerSuccess", model.asMap().get("registerSuccess"));



        model.addAttribute("groupedSeats", groupedSeats);
        return "seat-list"; //テストアクセス　http://localhost:8080/seats?storeId=1
    }
}
