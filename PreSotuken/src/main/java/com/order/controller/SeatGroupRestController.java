package com.order.controller;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.order.entity.SeatGroup;
import com.order.entity.Store;
import com.order.repository.SeatGroupRepository;
import com.order.repository.StoreRepository;

@RestController
@RequestMapping("/api/seat-groups")
public class SeatGroupRestController {

    @Autowired
    private SeatGroupRepository seatGroupRepository;

    @Autowired
    private StoreRepository storeRepository;

    // グループ一覧（storeId指定）
    @GetMapping("/by-store/{storeId}")
    public List<SeatGroup> getGroups(@PathVariable int storeId) {
        return seatGroupRepository.findByStore_StoreId(storeId);
    }

    // グループ追加
    @PostMapping
    public SeatGroup createGroup(@RequestBody SeatGroup group) {
        // フロントエンドから送信されたstoreオブジェクトのIDを使用して、
        // データベースから実際のStoreエンティティを取得する
        if (group.getStore() != null && group.getStore().getStoreId() != null) {
            Store store = storeRepository.findById(group.getStore().getStoreId())
                .orElseThrow(() -> new RuntimeException("Store not found: " + group.getStore().getStoreId()));
            group.setStore(store);
        }
        return seatGroupRepository.save(group);
    }

    // グループ編集
    @PutMapping("/{groupId}")
    public SeatGroup updateGroup(@PathVariable int groupId, @RequestBody SeatGroup updated) {
        Optional<SeatGroup> existingOpt = seatGroupRepository.findById(groupId);
        if (existingOpt.isEmpty()) {
            throw new RuntimeException("SeatGroup not found: " + groupId);
        }
        SeatGroup existing = existingOpt.get();
        existing.setSeatGroupName(updated.getSeatGroupName());
        return seatGroupRepository.save(existing);
    }

    // グループ削除
    @DeleteMapping("/{groupId}")
    public void deleteGroup(@PathVariable int groupId) {
        seatGroupRepository.deleteById(groupId);
    }
}
