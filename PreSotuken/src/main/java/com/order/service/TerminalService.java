package com.order.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.order.dto.TerminalCreationDto;
import com.order.entity.Seat;
import com.order.entity.Store;
import com.order.entity.Terminal;
import com.order.repository.SeatRepository;
import com.order.repository.StoreRepository;
import com.order.repository.TerminalRepository;

import lombok.RequiredArgsConstructor;

/**
 * 端末管理に関するビジネスロジックを提供するサービス
 */
@Service
@RequiredArgsConstructor
public class TerminalService {

    private final TerminalRepository terminalRepository;
    private final SeatRepository seatRepository;
    private final StoreRepository storeRepository;

    /**
     * 新しい端末を作成します
     * 
     * @param dto 端末作成用DTO
     * @param storeId 店舗ID
     * @throws IllegalArgumentException 入力データが不正な場合
     */
    public void createTerminal(TerminalCreationDto dto, Integer storeId) {
        if (storeId == null) {
            throw new IllegalArgumentException("店舗IDが指定されていません。");
        }

        Store store = storeRepository.findById(storeId)
            .orElseThrow(() -> new IllegalArgumentException("指定された店舗が見つかりません。"));

        // 管理者端末の場合は座席IDが不要
        Seat seat = null;
        if (!dto.isAdmin()) {
            if (dto.getSeatId() == null) {
                throw new IllegalArgumentException("座席IDが指定されていません。");
            }
            seat = seatRepository.findById(dto.getSeatId())
                .orElseThrow(() -> new IllegalArgumentException("指定された座席が見つかりません。"));
            
            // 座席が現在の店舗に属しているかのチェック
            if (!seat.getStore().getStoreId().equals(storeId)) {
                throw new IllegalArgumentException("選択された座席は現在の店舗に属していません。");
            }
        }

        String ipAddress = dto.getIpAddress();
        if (ipAddress == null || ipAddress.isEmpty()) {
            throw new IllegalArgumentException("IPアドレスが不正です。");
        }

        Terminal newTerminal = new Terminal();
        newTerminal.setSeat(seat);
        newTerminal.setStore(store);
        newTerminal.setIpAddress(ipAddress);
        newTerminal.setAdmin(dto.isAdmin());

        terminalRepository.save(newTerminal);
    }

    /**
     * 端末情報を更新します
     * 
     * @param terminalId 端末ID
     * @param dto 更新用DTO
     * @param storeId 店舗ID
     * @throws IllegalArgumentException 入力データが不正な場合
     */
    public void updateTerminal(Integer terminalId, TerminalCreationDto dto, Integer storeId) {
        if (storeId == null) {
            throw new IllegalArgumentException("店舗IDが指定されていません。");
        }

        Terminal existingTerminal = terminalRepository.findById(terminalId)
            .orElseThrow(() -> new IllegalArgumentException("指定された端末が見つかりません。"));

        // 端末が現在の店舗に属しているか確認
        if (!existingTerminal.getStore().getStoreId().equals(storeId)) {
            throw new IllegalArgumentException("この端末を編集する権限がありません。");
        }

        // 座席の更新（管理者端末でない場合）
        if (!dto.isAdmin()) {
            if (dto.getSeatId() == null) {
                throw new IllegalArgumentException("座席IDが指定されていません。");
            }
            
            Seat seat = seatRepository.findById(dto.getSeatId())
                .orElseThrow(() -> new IllegalArgumentException("指定された座席が見つかりません。"));
            
            // 更新後の座席も現在の店舗に属しているか確認
            if (!seat.getStore().getStoreId().equals(storeId)) {
                throw new IllegalArgumentException("選択された座席は現在の店舗に属していません。");
            }
            existingTerminal.setSeat(seat);
        } else {
            existingTerminal.setSeat(null);
        }

        // IPアドレスの更新
        String ipAddress = dto.getIpAddress();
        if (ipAddress == null || ipAddress.isEmpty()) {
            throw new IllegalArgumentException("IPアドレスが不正です。");
        }
        existingTerminal.setIpAddress(ipAddress);
        existingTerminal.setAdmin(dto.isAdmin());

        terminalRepository.save(existingTerminal);
    }

    /**
     * 端末を削除します
     * 
     * @param terminalId 端末ID
     * @param storeId 店舗ID
     * @throws IllegalArgumentException 入力データが不正な場合
     */
    public void deleteTerminal(Integer terminalId, Integer storeId) {
        if (storeId == null) {
            throw new IllegalArgumentException("店舗IDが指定されていません。");
        }

        Terminal existingTerminal = terminalRepository.findById(terminalId)
            .orElseThrow(() -> new IllegalArgumentException("指定された端末が見つかりません。"));

        // 端末が現在の店舗に属しているか確認
        if (!existingTerminal.getStore().getStoreId().equals(storeId)) {
            throw new IllegalArgumentException("この端末を削除する権限がありません。");
        }

        terminalRepository.delete(existingTerminal);
    }

    /**
     * 店舗の座席一覧を取得します
     * 
     * @param storeId 店舗ID
     * @return 座席のリスト
     * @throws IllegalArgumentException 店舗IDが不正な場合
     */
    public List<Seat> getSeatsByStoreId(Integer storeId) {
        if (storeId == null) {
            throw new IllegalArgumentException("店舗IDが指定されていません。");
        }

        storeRepository.findById(storeId)
            .orElseThrow(() -> new IllegalArgumentException("指定された店舗が見つかりません。"));

        return seatRepository.findByStore_StoreId(storeId);
    }

    /**
     * 店舗の端末一覧を取得します
     * 
     * @param storeId 店舗ID
     * @return 端末のリスト
     */
    public List<Terminal> getTerminalsByStoreId(Integer storeId) {
        return terminalRepository.findByStoreStoreId(storeId);
    }
}