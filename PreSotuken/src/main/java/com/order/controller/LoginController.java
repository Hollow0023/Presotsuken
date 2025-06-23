package com.order.controller;

import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.order.entity.Store;
import com.order.entity.Terminal;
import com.order.repository.StoreRepository;
import com.order.repository.TerminalRepository;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class LoginController {

	private final StoreRepository storeRepository;
	private final TerminalRepository terminalRepository;

	@GetMapping("/")
	public String showLoginForm(HttpServletRequest request, HttpServletResponse response, Model model) {
		Cookie[] cookies = request.getCookies();
		Integer storeId = null;
		String storeName = null;

		// cookieにstoreIDがある場合、IDから店舗名を求めて保存する
		if (cookies != null) {
			for (Cookie cookie : cookies) {
				switch (cookie.getName()) {
				case "storeId":
					try {
						storeId = Integer.parseInt(cookie.getValue());
					} catch (NumberFormatException e) {
						storeId = null;
					}
					break;
				case "storeName":
					storeName = cookie.getValue();
					break;
				}
			}
		}

		// 両方取得できていれば、自動ログインを試行
		if (storeId != null && storeName != null) {
			final String cookieStoreName = storeName;
			Store found = storeRepository.findById(storeId)
					.filter(s -> s.getStoreName().equals(cookieStoreName))
					.orElse(null);

			if (found != null) {
				return "redirect:/seats"; // クッキーが有効ならそのままログイン成功
			}
		}
		// 通常ログイン画面
		model.addAttribute("store", new Store());
		return "login";
	}

	@PostMapping("/login")
	public String login(@RequestParam int storeId,
			@RequestParam String storeName,
			HttpServletRequest request,
			HttpServletResponse response) {

		boolean isLoginSuccess = storeRepository.existsByStoreIdAndStoreName(storeId, storeName);
		if (isLoginSuccess) {
			return "redirect:/login?error=true";
		}

		String clientIp = getClientIp(request);
		Optional<Terminal> optTerminal = terminalRepository.findByIpAddressAndStore_StoreId(clientIp, storeId);
		if (optTerminal.isEmpty()) {
			return "redirect:/login?error=terminalNotFound";
		}

		Terminal terminal = optTerminal.get();

		// クッキー保存
		addCookie(response, "storeId", String.valueOf(storeId));
		addCookie(response, "terminalId", String.valueOf(terminal.getTerminalId()));
		addCookie(response, "adminFlag", String.valueOf(terminal.isAdmin()));
		if (!terminal.isAdmin()) {
			addCookie(response, "seatId", String.valueOf(terminal.getSeat().getSeatId()));
		}

		// 遷移先
		return terminal.isAdmin()
				? "redirect:/seats"
				: "redirect:/visits/orderwait";
	}
	
	// クッキー設定用ヘルパー
	private void addCookie(HttpServletResponse response, String name, String value) {
	    Cookie cookie = new Cookie(name, value);
	    cookie.setPath("/");
	    response.addCookie(cookie);
	}

	// IPアドレス取得
	private String getClientIp(HttpServletRequest request) {
	    String xfHeader = request.getHeader("X-Forwarded-For");
	    return (xfHeader == null) ? request.getRemoteAddr() : xfHeader.split(",")[0];
	}
	//    public String login(@ModelAttribute Store store, HttpServletResponse response, Model model) {
	//        Store found = storeRepository.findById(store.getStoreId())
	//                .filter(s -> s.getStoreName().equals(store.getStoreName()))
	//                .orElse(null);
	//
	//        if (found != null) {
	//            // クッキーを保存（120日）
	//            Cookie idCookie = new Cookie("storeId", String.valueOf(found.getStoreId()));
	//            Cookie nameCookie = new Cookie("storeName", found.getStoreName());
	//            idCookie.setPath("/");
	//            nameCookie.setPath("/");
	//            idCookie.setMaxAge(60 * 60 * 24 * 120);    // 120日間
	//            nameCookie.setMaxAge(60 * 60 * 24 * 120);
	//            response.addCookie(idCookie);
	//            response.addCookie(nameCookie);
	//
	//            return "redirect:/seats";
	//        } else {
	//            model.addAttribute("error", "店舗情報が一致しません");
	//            return "login";
	//        }
	//    }

	@GetMapping("/api/stores/check")
	@ResponseBody
	public Map<String, Boolean> checkStore(@RequestParam Integer storeId, @RequestParam String storeName) {
		boolean valid = storeRepository.existsByStoreIdAndStoreName(storeId, storeName);
		return Map.of("valid", valid);
	}
}