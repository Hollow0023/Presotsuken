package com.order.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

@Controller
public class LogoutController {

	@GetMapping("/logout")
	public String logout(HttpServletResponse response) {
	    for (String name : List.of("storeId", "adminFlag", "terminalId", "seatId","storeName")) {
	        Cookie cookie = new Cookie(name, null);
	        cookie.setMaxAge(0);
	        cookie.setPath("/");
	        response.addCookie(cookie);
	    }
	    return "redirect:/login?logout=true";
	}
}
