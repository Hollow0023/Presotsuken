package com.order.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.order.entity.MenuTimeSlot;
import com.order.entity.Store;
import com.order.repository.MenuTimeSlotRepository;
import com.order.repository.StoreRepository;

import lombok.RequiredArgsConstructor;

/**
 * メニュー時間帯の管理を行うサービスクラス
 */
@Service
@RequiredArgsConstructor
public class MenuTimeSlotService {

    private final MenuTimeSlotRepository menuTimeSlotRepository;
    private final StoreRepository storeRepository;

    /**
     * 指定した店舗の時間帯一覧を取得します
     * 
     * @param storeId 店舗ID
     * @return 時間帯のリスト
     */
    public List<MenuTimeSlot> getTimeSlotsByStoreId(int storeId) {
        return menuTimeSlotRepository.findByStoreStoreId(storeId);
    }

    /**
     * 新規時間帯を作成します
     * 
     * @param timeSlot 時間帯情報
     * @param storeId 店舗ID
     * @return 作成した時間帯
     */
    @Transactional
    public MenuTimeSlot createTimeSlot(MenuTimeSlot timeSlot, int storeId) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new IllegalArgumentException("Store with ID " + storeId + " not found."));
        timeSlot.setStore(store);
        return menuTimeSlotRepository.save(timeSlot);
    }

    /**
     * 時間帯を更新します
     * 
     * @param updatedTimeSlot 更新する時間帯情報
     * @return 更新した時間帯
     */
    @Transactional
    public MenuTimeSlot updateTimeSlot(MenuTimeSlot updatedTimeSlot) {
        return menuTimeSlotRepository.findById(updatedTimeSlot.getTimeSlotId())
                .map(existing -> {
                    existing.setName(updatedTimeSlot.getName());
                    existing.setStartTime(updatedTimeSlot.getStartTime());
                    existing.setEndTime(updatedTimeSlot.getEndTime());
                    return menuTimeSlotRepository.save(existing);
                })
                .orElseThrow(() -> new IllegalArgumentException(
                        "TimeSlot with ID " + updatedTimeSlot.getTimeSlotId() + " not found."));
    }

    /**
     * 時間帯を削除します
     * 
     * @param timeSlotId 時間帯ID
     */
    @Transactional
    public void deleteTimeSlot(int timeSlotId) {
        if (!menuTimeSlotRepository.existsById(timeSlotId)) {
            throw new IllegalArgumentException("TimeSlot with ID " + timeSlotId + " not found.");
        }
        menuTimeSlotRepository.deleteById(timeSlotId);
    }
}
