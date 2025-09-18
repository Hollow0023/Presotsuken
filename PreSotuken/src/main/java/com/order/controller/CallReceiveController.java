package com.order.controller;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController; // @RestController を使う

import com.order.entity.Seat;
import com.order.repository.SeatRepository;

import lombok.RequiredArgsConstructor;

@RestController // @Controller の代わりに @RestController を使うと @ResponseBody が不要になる
@RequiredArgsConstructor
public class CallReceiveController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private final SeatRepository seatRepository;
    // @Autowired
    // private SeatService seatService; // 座席名を取得するためのサービス（仮）

    // 座席IDを受け取るためのシンプルなリクエストボディ用クラス
    public static class CallRequest {
        private String seatId;
        // getter, setter, constructor
        public String getSeatId() { return seatId; }
        public void setSeatId(String seatId) { this.seatId = seatId; }
    }

    // HTMLからのPOSTリクエストを受け取るエンドポイント
    @PostMapping("/callSeat") // HTMLから /callSeat にPOSTリクエストが来たらここが呼ばれる
    public String receiveCall(@RequestBody CallRequest request) {
        String seatId = request.getSeatId();

        // 座席IDから座席名を検索する処理 (例: データベースから取得)
        // String seatName = seatService.findSeatNameById(seatId); 
        // 今回は簡略化のため、seatIdをそのままseatNameとして使うか、固定値とする
        Seat seat =  seatRepository.findBySeatId(Integer.parseInt(seatId));
        String seatName = seat.getSeatName();// 例: "A1" なら "座席 A1"

        // レジ端末に送る通知データをMapで作成
        Map<String, Object> notification = new HashMap<>();
        notification.put("seatId", seatId); // 必要ならseatIdも通知に含める
        notification.put("seatName", seatName);
        notification.put("callTime", LocalDateTime.now()); 

        // レジ端末が購読しているトピックにメッセージをブロードキャスト
        messagingTemplate.convertAndSend("/topic/seatCalls", notification);

        // HTTPリクエストなので、何かしらレスポンスを返す
        return "Call request received and broadcasted.";
    }
}