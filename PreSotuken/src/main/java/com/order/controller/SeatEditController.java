package com.order.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.order.dto.SeatRequestDto;
import com.order.dto.SeatUpdateDto;
import com.order.entity.Seat;
import com.order.entity.SeatGroup;
import com.order.repository.SeatGroupRepository;
import com.order.service.SeatService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/seat")
public class SeatEditController {


    private final SeatGroupRepository seatGroupRepository;
    private final SeatService seatService;

    @GetMapping("/edit")
    public String showSeatEditPage(HttpServletRequest request, Model model) {
        int storeId = getStoreIdFromCookie(request);
        List<SeatGroup> seatGroups = seatGroupRepository.findByStore_StoreId(storeId);
        model.addAttribute("seatGroups", seatGroups);
        return "admin/seatEdit";
    }

    private int getStoreIdFromCookie(HttpServletRequest request) {
        for (Cookie cookie : request.getCookies()) {
            if ("storeId".equals(cookie.getName())) {
                return Integer.parseInt(cookie.getValue());
            }
        }
        throw new RuntimeException("storeId cookie not found");
    }
    

    @PostMapping("/save")
    @ResponseBody
    public Seat createSeat(@RequestBody SeatRequestDto dto) {
        return seatService.createSeat(dto);
    }
    
    @GetMapping("/by-group/{groupId}")
    @ResponseBody
    public List<Seat> getSeatsByGroup(@PathVariable int groupId) {
        return seatService.getSeatsByGroupId(groupId);
    }
    
    @PostMapping("/update")
    @ResponseBody
    public Seat updateSeat(@RequestBody SeatUpdateDto dto) {
        return seatService.updateSeat(dto);
    }
    @DeleteMapping("/delete/{seatId}")
    @ResponseBody
    public void deleteSeat(@PathVariable int seatId) {
        seatService.deleteSeat(seatId);
    }
        
    
}
