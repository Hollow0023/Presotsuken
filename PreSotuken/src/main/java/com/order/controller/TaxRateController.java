package com.order.controller;

import java.util.List;

import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.order.entity.TaxRate;
import com.order.repository.TaxRateRepository;

@RestController
@RequestMapping("/taxrates")
@CrossOrigin
public class TaxRateController {

    private final TaxRateRepository taxRateRepository;

    public TaxRateController(TaxRateRepository taxRateRepository) {
        this.taxRateRepository = taxRateRepository;
    }

    @GetMapping
    public List<TaxRate> getByStore(@CookieValue("storeId") Integer storeId) {
        return taxRateRepository.findByStore_StoreId(storeId);
    }
}
