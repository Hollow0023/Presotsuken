package com.order.controller;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.order.entity.Payment;
import com.order.entity.PaymentDetail;
import com.order.entity.User;
import com.order.entity.Visit;
import com.order.repository.PaymentDetailRepository;
import com.order.repository.PaymentRepository;
import com.order.repository.UserRepository;
import com.order.repository.VisitRepository;
import com.order.service.PaymentLookupService;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class VisitInfoController {

	private final VisitRepository visitRepository;
	private final PaymentRepository paymentRepository;
	private final PaymentDetailRepository paymentDetailRepository;
	private final SimpMessagingTemplate messagingTemplate;
	private final UserRepository userRepository;

	@Autowired
	private PaymentLookupService paymentLookupService;

	@GetMapping("/visit-info")
	public Map<String, Object> getVisitInfo(@RequestParam("seatId") int seatId, @RequestParam("storeId") int storeId) {
		Map<String, Object> result = new HashMap<>();
		//        Visit visit = visitRepository.findFirstBySeat_SeatIdAndLeaveTimeIsNullOrderByVisitTimeDesc(seatId);
		Visit visit = visitRepository
				.findFirstBySeat_Store_StoreIdAndSeat_SeatIdAndLeaveTimeIsNullOrderByVisitTimeDesc(storeId, seatId);

		if (visit != null) {
			result.put("visiting", true);
			result.put("visitId", visit.getVisitId());
			result.put("numberOfPeople", visit.getNumberOfPeople());

			LocalDateTime now = LocalDateTime.now();
			long minutes = Duration.between(visit.getVisitTime(), now).toMinutes();
			result.put("elapsedMinutes", minutes);
		} else {
			result.put("visiting", false);
		}

		return result;
	}

	@DeleteMapping("/delete-visit") // エンドポイント名はdeleteのままだが、処理は会計確定
    @Transactional
    public ResponseEntity<Void> deleteVisitAndPayment(@RequestParam("seatId") int seatId,
                                                   @CookieValue(name = "userId", required = false) Integer userId) { // userIdをCookieから取得
        // まだ退店していない最新のVisitを取得
        Visit visit = visitRepository.findFirstBySeat_SeatIdAndLeaveTimeIsNullOrderByVisitTimeDesc(seatId);
        if (visit == null) {
            return ResponseEntity.notFound().build(); // Visitがない場合は終了
        }

        // Visitに紐づくPaymentを取得 - 個別会計機能対応: 親会計（元の会計）のみを取得
        Payment payment = paymentRepository.findByVisitVisitIdAndParentPaymentIsNull(visit.getVisitId()); 
        
        // もし、既に会計確定済み（payment.getVisitCancel() == true）の場合は、何もしない、あるいはエラーを返す
        if (payment != null && Boolean.TRUE.equals(payment.getVisitCancel())) {
            return ResponseEntity.badRequest().build(); 
        }
        
        if (payment != null) {
            // 会計確定の処理を行う
            LocalDateTime paymentTime = LocalDateTime.now(); // 会計確定時刻を現在時刻とする

            // Cashierを設定 (Userリポジトリから取得)
            User cashier = null;
            if (userId != null) { // userIdがCookieから取得できた場合
                cashier = userRepository.findById(userId).orElse(null);
            } else {
                // userIdがCookieから取得できない場合のデフォルト処理やエラーハンドリング
                // 例: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build(); // 認証されていない
            }

            // Paymentテーブルに設定する項目
            payment.setPaymentTime(paymentTime);     // PaymentTimeを設定
            payment.setCashier(cashier);             // Cashierを設定
            payment.setVisitCancel(true);            // Paymentのvisit_cancelをtrueにする (会計確定とみなすフラグ)
                                                     // deposit, subtotal, total, discount はデフォルトのまま（設定しない）

            paymentRepository.save(payment); // Paymentエンティティを更新

            // Visitテーブルに設定する項目
            visit.setLeaveTime(paymentTime);         // Visitの退店時刻を設定
                                                     // 他の項目はデフォルトのままでOK
            visitRepository.save(visit);     // Visitエンティティを更新

            // WebSocket通知は引き続き送信（座席が空いたことを示すため）
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "LEAVE");
            payload.put("seatId", seatId); 

            messagingTemplate.convertAndSend("/topic/seats/" + seatId, payload);


            return ResponseEntity.ok().build();
        } else {
            // Paymentがない場合は、Paymentがまだ作成されていない状態。
            // 会計確定はPaymentが存在しないとできないため、notFoundを返す。
            return ResponseEntity.notFound().build();
        }
    }


	@GetMapping("/total-amount")
	public Map<String, Object> getTotalAmount(@RequestParam int seatId) {
		Payment payment = paymentLookupService.findPaymentBySeatId(seatId);
		if (payment == null) {
			return Map.of("total", 0);
		}

		List<PaymentDetail> details = paymentDetailRepository.findByPaymentPaymentId(payment.getPaymentId());

		int total = 0;
		for (PaymentDetail d : details) {
			double rate = d.getTaxRate().getRate();
			int subtotal = (int) Math.round(d.getMenu().getPrice() * d.getQuantity() * (1 + rate));
			total += subtotal;
		}

		return Map.of("total", total);
	}

}
