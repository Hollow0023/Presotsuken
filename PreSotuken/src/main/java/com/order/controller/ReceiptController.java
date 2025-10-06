package com.order.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.order.dto.PaymentSummaryDto;
import com.order.dto.ReceiptIssueRequest;
import com.order.dto.ReceiptResponseDto;
import com.order.entity.Receipt;
import com.order.service.PrintService;
import com.order.service.ReceiptService;

import lombok.RequiredArgsConstructor;

/**
 * 領収書コントローラー
 */
@Controller
@RequiredArgsConstructor
public class ReceiptController {

    private final ReceiptService receiptService;
    private final PrintService printService;

    /**
     * 領収書を発行する
     */
    @PostMapping("/api/receipts/issue")
    @Transactional
    public ResponseEntity<ReceiptResponseDto> issueReceipt(@RequestBody ReceiptIssueRequest request) {
        try {
            // 領収書を発行
            Receipt receipt = receiptService.issueReceipt(request);

            // 印刷
            printService.printReceipt(receipt, receipt.getStore().getStoreId(), false);

            // レスポンスを作成
            List<ReceiptResponseDto> receipts = receiptService.getReceiptsByPaymentId(request.getPaymentId());
            ReceiptResponseDto issuedReceipt = receipts.stream()
                .filter(r -> r.getReceiptId().equals(receipt.getReceiptId()))
                .findFirst()
                .orElse(null);

            return ResponseEntity.ok(issuedReceipt);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 領収書を再印字する
     */
    @PostMapping("/api/receipts/{receiptId}/reprint")
    @Transactional
    public ResponseEntity<Void> reprintReceipt(@PathVariable Integer receiptId) {
        try {
            Receipt receipt = receiptService.reprintReceipt(receiptId);
            printService.printReceipt(receipt, receipt.getStore().getStoreId(), true);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 領収書を取消する
     */
    @PostMapping("/api/receipts/{receiptId}/void")
    @Transactional
    public ResponseEntity<Void> voidReceipt(
            @PathVariable Integer receiptId,
            @RequestParam Integer userId) {
        try {
            receiptService.voidReceipt(receiptId, userId);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 会計の領収書一覧を取得
     */
    @GetMapping("/api/payments/{paymentId}/receipts")
    public ResponseEntity<List<ReceiptResponseDto>> getReceiptsByPaymentId(@PathVariable Integer paymentId) {
        List<ReceiptResponseDto> receipts = receiptService.getReceiptsByPaymentId(paymentId);
        return ResponseEntity.ok(receipts);
    }

    /**
     * 会計サマリ（領収書発行残高含む）を取得
     */
    @GetMapping("/api/payments/{paymentId}/summary")
    public ResponseEntity<PaymentSummaryDto> getPaymentSummary(@PathVariable Integer paymentId) {
        PaymentSummaryDto summary = receiptService.calculatePaymentSummary(paymentId);
        return ResponseEntity.ok(summary);
    }
}
