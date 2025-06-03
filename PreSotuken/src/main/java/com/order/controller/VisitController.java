package com.order.controller;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.order.entity.Payment;
import com.order.entity.Seat;
import com.order.entity.Store;
import com.order.entity.Terminal;
import com.order.entity.Visit;
import com.order.repository.PaymentRepository;
import com.order.repository.SeatRepository;
import com.order.repository.StoreRepository;
import com.order.repository.TerminalRepository;
import com.order.repository.VisitRepository;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor //コンストラクタ作ってくれる(final付き変数に対して)
@RequestMapping("/visits")
public class VisitController {

	private final VisitRepository visitRepository;
	private final PaymentRepository paymentRepository;
	private final StoreRepository storeRepository;
	private final SeatRepository seatRepository;
	private final TerminalRepository terminalRepository;
	private final SimpMessagingTemplate messagingTemplate;

	@PostMapping
	public String createVisit(@RequestParam("seat.seatId") Integer seatId,
			@RequestParam("store.storeId") Integer storeId,
			@RequestParam("user.userId") Integer userId,
			@RequestParam("numberOfPeople") Integer numberOfPeople,
			RedirectAttributes redirectAttributes) {

		// 店舗・座席・ユーザーを取得
		Store store = storeRepository.findById(storeId).orElseThrow();
		Seat seat = seatRepository.findById(seatId).orElseThrow();

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
		Payment savedPayment = paymentRepository.save(payment);

		// WebSocket 通知
		if (savedVisit.getVisitId() != null && savedPayment.getPaymentId() != null) {
			Map<String, Object> payload = new HashMap<>();
			payload.put("visitId", savedVisit.getVisitId());
			payload.put("storeId", storeId);
			payload.put("userId", userId);
			payload.put("seatId", seatId);
			messagingTemplate.convertAndSend("/topic/seats/" + seatId, payload);

			redirectAttributes.addFlashAttribute("registerSuccess", true);
			return "redirect:/seats?storeId=" + storeId;
		} else {
			return "/error";
		}
	}

	@GetMapping("/orderwait")
	public String orderWaitPage(@CookieValue(value = "storeId", required = false) Integer storeId,
			HttpServletRequest request,
			HttpServletResponse response,
			Model model) {
		if (storeId == null) {
			return "redirect:/tableLogin"; // storeIdがないならログインページへ
		}

		String ip = request.getRemoteAddr();
		if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) { //テスト環境のみ（サーバー自身からアクセスされた場合、127.0.0.1に変換）
			ip = "127.0.0.1";
		}

		Terminal terminal = terminalRepository.findByIpAddressAndStore_StoreId(ip, storeId)
				.orElseThrow(() -> new RuntimeException("端末が見つかりません"));

		Integer seatId = terminal.getSeat().getSeatId();
		model.addAttribute("seatId", seatId);
		model.addAttribute("storeId", storeId);

		// Cookie に seatId を保存
		Cookie seatIdCookie = new Cookie("seatId", String.valueOf(seatId));
		seatIdCookie.setPath("/");
		seatIdCookie.setMaxAge(60 * 60 * 24 * 120);
		response.addCookie(seatIdCookie);

		return "orderwait";
	}
}
