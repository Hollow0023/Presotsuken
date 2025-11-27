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

import com.order.entity.Store;
import com.order.entity.TaxRate;
import com.order.repository.StoreRepository;
import com.order.repository.TaxRateRepository;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/taxrates")
@RequiredArgsConstructor
public class TaxRateController {

    private final TaxRateRepository taxRateRepository;
    private final StoreRepository storeRepository;

    /**
     * 税率設定ページを表示
     */
    @GetMapping("/setting")
    public String showTaxRateSettingPage(@CookieValue("storeId") int storeId, Model model) {
        model.addAttribute("storeId", storeId);
        model.addAttribute("taxRates", taxRateRepository.findByStore_StoreId(storeId));
        return "tax_rate_setting";
    }

    /**
     * 店舗の税率一覧を取得（API）
     */
    @GetMapping
    @ResponseBody
    public List<TaxRate> getByStore(@CookieValue("storeId") Integer storeId) {
        return taxRateRepository.findByStore_StoreId(storeId);
    }

    /**
     * 新規税率を作成
     */
    @PostMapping
    @ResponseBody
    public ResponseEntity<TaxRate> createTaxRate(@RequestBody TaxRate taxRate,
                                                  @CookieValue("storeId") int storeId) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new IllegalArgumentException("店舗が見つかりません"));
        taxRate.setStore(store);
        TaxRate created = taxRateRepository.save(taxRate);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    /**
     * 税率を更新
     */
    @PutMapping("/{taxRateId}")
    @ResponseBody
    public ResponseEntity<TaxRate> updateTaxRate(@PathVariable int taxRateId,
                                                  @RequestBody TaxRate taxRate,
                                                  @CookieValue("storeId") int storeId) {
        return taxRateRepository.findByTaxRateId(taxRateId)
                .map(existing -> {
                    if (!isOwnedByStore(existing, storeId)) {
                        return new ResponseEntity<TaxRate>(HttpStatus.FORBIDDEN);
                    }
                    existing.setRate(taxRate.getRate());
                    TaxRate updated = taxRateRepository.save(existing);
                    return ResponseEntity.ok(updated);
                })
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    /**
     * 税率を削除
     */
    @DeleteMapping("/{taxRateId}")
    @ResponseBody
    public ResponseEntity<Void> deleteTaxRate(@PathVariable int taxRateId,
                                               @CookieValue("storeId") int storeId) {
        return taxRateRepository.findByTaxRateId(taxRateId)
                .map(existing -> {
                    if (!isOwnedByStore(existing, storeId)) {
                        return new ResponseEntity<Void>(HttpStatus.FORBIDDEN);
                    }
                    taxRateRepository.delete(existing);
                    return new ResponseEntity<Void>(HttpStatus.NO_CONTENT);
                })
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    /**
     * 税率が指定した店舗に属しているか確認
     */
    private boolean isOwnedByStore(TaxRate taxRate, int storeId) {
        return taxRate.getStore().getStoreId().equals(storeId);
    }
}
