package com.order.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.order.dto.CashTransactionRequest;
import com.order.service.CashTransactionService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/admin/cash")
@RequiredArgsConstructor
public class CashTransactionController {

    private final CashTransactionService transactionService;

    @PostMapping("/transaction")
    public String registerTransaction(@ModelAttribute @Valid CashTransactionRequest request,
                                      HttpServletRequest httpRequest) {
        transactionService.saveTransaction(request, httpRequest);
        return "redirect:/admin/cash/transaction?success=true";
    }

    @GetMapping("/transaction")
    public String showTransactionForm(HttpServletRequest request, Model model) {
        model.addAttribute("cashTransactionRequest", new CashTransactionRequest());
        model.addAttribute("users", transactionService.getUsersForStore(request));
        return "admin/cashTransactionForm";
    }

}
