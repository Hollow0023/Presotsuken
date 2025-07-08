package com.order.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.order.dto.SeatRequestDto;
import com.order.dto.SeatUpdateDto;
import com.order.entity.Seat;
import com.order.entity.SeatGroup;
import com.order.entity.Store;
import com.order.repository.SeatGroupRepository;
import com.order.repository.SeatRepository;
import com.order.repository.StoreRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SeatService {

    private final SeatRepository seatRepository;
    private final SeatGroupRepository seatGroupRepository;
    private final StoreRepository storeRepository;
    
    
    public List<Seat> getSeatsByGroupId(int seatGroupId) {
        return seatRepository.findBySeatGroup_SeatGroupIdOrderBySeatNameAsc(seatGroupId);
    }


    public Seat createSeat(SeatRequestDto dto) {
        SeatGroup group = seatGroupRepository.findById(dto.seatGroupId)
            .orElseThrow(() -> new RuntimeException("SeatGroup not found"));

        Store store = storeRepository.findById(dto.storeId)
            .orElseThrow(() -> new RuntimeException("Store not found"));

        Seat seat = new Seat();
        seat.setSeatGroup(group);
        seat.setSeatName(dto.seatName);
        seat.setMaxCapacity(dto.maxCapacity);
        seat.setStore(store); // ← ここ重要

        return seatRepository.save(seat);
    }
    
    public Seat updateSeat(SeatUpdateDto dto) {
        Seat seat = seatRepository.findById(dto.seatId)
            .orElseThrow(() -> new RuntimeException("Seat not found"));

        seat.setSeatName(dto.seatName);
        seat.setMaxCapacity(dto.maxCapacity);

        return seatRepository.save(seat);
    }

    
    public void deleteSeat(int seatId) {
        seatRepository.deleteById(seatId);
    }
}
