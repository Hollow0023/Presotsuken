package com.order.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.order.entity.PaymentType;
import com.order.service.PaymentTypeService;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/payment-types")
@RequiredArgsConstructor
public class PaymentTypeController {

    private final PaymentTypeService paymentTypeService;

    @GetMapping
    public String showPaymentTypePage(@CookieValue("storeId") int storeId, Model model) {
        model.addAttribute("storeId", storeId);
        model.addAttribute("paymentTypes", paymentTypeService.getPaymentTypesByStoreId(storeId));
        return "payment_type_management";
    }

    @GetMapping("/by-store/{storeId}")
    @ResponseBody
    public List<PaymentType> getPaymentTypes(@PathVariable int storeId) {
        return paymentTypeService.getPaymentTypesByStoreId(storeId);
    }

    @PostMapping
    @ResponseBody
    public ResponseEntity<PaymentType> createPaymentType(@RequestBody PaymentType paymentType) {
        PaymentType created = paymentTypeService.createPaymentType(paymentType);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @PutMapping("/{typeId}")
    @ResponseBody
    public ResponseEntity<PaymentType> updatePaymentType(@PathVariable int typeId,
                                                         @RequestBody PaymentType paymentType) {
        if (typeId != paymentType.getTypeId()) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        try {
            PaymentType updated = paymentTypeService.updatePaymentType(paymentType);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @DeleteMapping("/{typeId}")
    @ResponseBody
    public ResponseEntity<Void> deletePaymentType(@PathVariable int typeId) {
        try {
            paymentTypeService.deletePaymentType(typeId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }
}

