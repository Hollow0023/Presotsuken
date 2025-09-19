// CallReceiveController.java (例: 名前を変えた方が分かりやすいかも)
package com.order.controller;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.order.entity.Seat;
import com.order.service.SeatService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class CallReceiveController {

    private final SimpMessagingTemplate messagingTemplate;
    private final SeatService seatService;

    // 座席IDを受け取るためのシンプルなリクエストボディ用クラス
    public static class CallRequest {
        private String seatId;
        // getter, setter, constructor
        public String getSeatId() { return seatId; }
        public void setSeatId(String seatId) { this.seatId = seatId; }
    }

    // ★ HTMLからのPOSTリクエストを受け取るエンドポイント ★
    @PostMapping("/callSeat") // HTMLから /callSeat にPOSTリクエストが来たらここが呼ばれる
    public String receiveCall(@RequestBody CallRequest request) {
        String seatId = request.getSeatId();

        // 座席IDから座席名を検索する処理をServiceに委任
        Seat seat = seatService.findSeatById(Integer.parseInt(seatId));
        String seatName = seat.getSeatName();

        // レジ端末に送る通知データをMapで作成
        Map<String, Object> notification = new HashMap<>();
        notification.put("seatId", seatId); // 必要ならseatIdも通知に含める
        notification.put("seatName", seatName);
        notification.put("callTime", LocalDateTime.now()); 

        // ★ レジ端末が購読しているトピックにメッセージをブロードキャスト
        messagingTemplate.convertAndSend("/topic/seatCalls", notification);

        // HTTPリクエストなので、何かしらレスポンスを返す
        return "Call request received and broadcasted.";
    }
}