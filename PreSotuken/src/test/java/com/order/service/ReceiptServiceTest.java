package com.order.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.order.dto.PaymentSummaryDto;
import com.order.dto.ReceiptIssueRequest;
import com.order.entity.Menu;
import com.order.entity.Payment;
import com.order.entity.PaymentDetail;
import com.order.entity.Receipt;
import com.order.entity.Store;
import com.order.entity.TaxRate;
import com.order.entity.User;
import com.order.repository.PaymentDetailRepository;
import com.order.repository.PaymentRepository;
import com.order.repository.ReceiptRepository;
import com.order.repository.UserRepository;

/**
 * 領収書サービスのユニットテスト
 * 特に按分計算のロジックをテスト
 */
@ExtendWith(MockitoExtension.class)
public class ReceiptServiceTest {

    @Mock
    private ReceiptRepository receiptRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentDetailRepository paymentDetailRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ReceiptService receiptService;

    private Payment testPayment;
    private Store testStore;
    private User testUser;
    private TaxRate taxRate10;
    private TaxRate taxRate8;

    @BeforeEach
    void setUp() {
        // テストデータの準備
        testStore = new Store();
        testStore.setStoreId(1);
        testStore.setStoreName("テスト店舗");

        testUser = new User();
        testUser.setUserId(1);
        testUser.setUserName("テスト担当者");

        testPayment = new Payment();
        testPayment.setPaymentId(1);
        testPayment.setStore(testStore);
        testPayment.setTotal(1000.0);
        testPayment.setSubtotal(1000.0);
        testPayment.setDiscount(0.0);

        taxRate10 = new TaxRate();
        taxRate10.setTaxRateId(1);
        taxRate10.setRate(0.10);

        taxRate8 = new TaxRate();
        taxRate8.setTaxRateId(2);
        taxRate8.setRate(0.08);
    }

    @Test
    @DisplayName("税率10%のみの会計で会計サマリが正しく計算される")
    void testCalculatePaymentSummary_OnlyTax10() {
        // Arrange
        List<PaymentDetail> details = new ArrayList<>();
        
        Menu menu1 = new Menu();
        menu1.setPrice(500.0);
        
        PaymentDetail detail1 = new PaymentDetail();
        detail1.setMenu(menu1);
        detail1.setQuantity(2);
        detail1.setTaxRate(taxRate10);
        detail1.setSubtotal(1000.0);
        detail1.setDiscount(0.0);
        
        details.add(detail1);

        when(paymentRepository.findById(1)).thenReturn(Optional.of(testPayment));
        when(paymentDetailRepository.findByPaymentPaymentId(1)).thenReturn(details);
        when(receiptRepository.findByPaymentPaymentIdAndVoidedFalseOrderByIssuedAtDesc(1)).thenReturn(new ArrayList<>());

        // Act
        PaymentSummaryDto summary = receiptService.calculatePaymentSummary(1);

        // Assert
        assertNotNull(summary);
        assertEquals(1, summary.getPaymentId());
        
        // 税抜1000円、税額100円、税込1100円
        assertEquals(1000.0, summary.getNetAmount10(), 0.01);
        assertEquals(100.0, summary.getTaxAmount10(), 0.01);
        assertEquals(1100.0, summary.getGrossAmount10(), 0.01);
        
        // 8%は0
        assertEquals(0.0, summary.getNetAmount8(), 0.01);
        assertEquals(0.0, summary.getTaxAmount8(), 0.01);
        assertEquals(0.0, summary.getGrossAmount8(), 0.01);
        
        // 残高は全額（発行済みなし）
        assertEquals(1100.0, summary.getRemainingAmount10(), 0.01);
        assertEquals(0.0, summary.getRemainingAmount8(), 0.01);
        assertEquals(1100.0, summary.getRemainingTotal(), 0.01);
    }

    @Test
    @DisplayName("税率8%のみの会計で会計サマリが正しく計算される")
    void testCalculatePaymentSummary_OnlyTax8() {
        // Arrange
        List<PaymentDetail> details = new ArrayList<>();
        
        Menu menu1 = new Menu();
        menu1.setPrice(500.0);
        
        PaymentDetail detail1 = new PaymentDetail();
        detail1.setMenu(menu1);
        detail1.setQuantity(2);
        detail1.setTaxRate(taxRate8);
        detail1.setSubtotal(1000.0);
        detail1.setDiscount(0.0);
        
        details.add(detail1);

        when(paymentRepository.findById(1)).thenReturn(Optional.of(testPayment));
        when(paymentDetailRepository.findByPaymentPaymentId(1)).thenReturn(details);
        when(receiptRepository.findByPaymentPaymentIdAndVoidedFalseOrderByIssuedAtDesc(1)).thenReturn(new ArrayList<>());

        // Act
        PaymentSummaryDto summary = receiptService.calculatePaymentSummary(1);

        // Assert
        assertNotNull(summary);
        
        // 10%は0
        assertEquals(0.0, summary.getNetAmount10(), 0.01);
        assertEquals(0.0, summary.getTaxAmount10(), 0.01);
        assertEquals(0.0, summary.getGrossAmount10(), 0.01);
        
        // 税抜1000円、税額80円、税込1080円
        assertEquals(1000.0, summary.getNetAmount8(), 0.01);
        assertEquals(80.0, summary.getTaxAmount8(), 0.01);
        assertEquals(1080.0, summary.getGrossAmount8(), 0.01);
        
        // 残高は全額
        assertEquals(0.0, summary.getRemainingAmount10(), 0.01);
        assertEquals(1080.0, summary.getRemainingAmount8(), 0.01);
        assertEquals(1080.0, summary.getRemainingTotal(), 0.01);
    }

    @Test
    @DisplayName("税率混在の会計で会計サマリが正しく計算される")
    void testCalculatePaymentSummary_MixedTaxRates() {
        // Arrange
        List<PaymentDetail> details = new ArrayList<>();
        
        // 10%の商品
        Menu menu1 = new Menu();
        menu1.setPrice(500.0);
        PaymentDetail detail1 = new PaymentDetail();
        detail1.setMenu(menu1);
        detail1.setQuantity(1);
        detail1.setTaxRate(taxRate10);
        detail1.setSubtotal(500.0);
        detail1.setDiscount(0.0);
        
        // 8%の商品
        Menu menu2 = new Menu();
        menu2.setPrice(300.0);
        PaymentDetail detail2 = new PaymentDetail();
        detail2.setMenu(menu2);
        detail2.setQuantity(1);
        detail2.setTaxRate(taxRate8);
        detail2.setSubtotal(300.0);
        detail2.setDiscount(0.0);
        
        details.add(detail1);
        details.add(detail2);

        testPayment.setSubtotal(800.0);
        testPayment.setTotal(874.0); // 500*1.1 + 300*1.08 = 550 + 324 = 874

        when(paymentRepository.findById(1)).thenReturn(Optional.of(testPayment));
        when(paymentDetailRepository.findByPaymentPaymentId(1)).thenReturn(details);
        when(receiptRepository.findByPaymentPaymentIdAndVoidedFalseOrderByIssuedAtDesc(1)).thenReturn(new ArrayList<>());

        // Act
        PaymentSummaryDto summary = receiptService.calculatePaymentSummary(1);

        // Assert
        assertNotNull(summary);
        
        // 10%: 税抜500、税額50、税込550
        assertEquals(500.0, summary.getNetAmount10(), 0.01);
        assertEquals(50.0, summary.getTaxAmount10(), 0.01);
        assertEquals(550.0, summary.getGrossAmount10(), 0.01);
        
        // 8%: 税抜300、税額24、税込324
        assertEquals(300.0, summary.getNetAmount8(), 0.01);
        assertEquals(24.0, summary.getTaxAmount8(), 0.01);
        assertEquals(324.0, summary.getGrossAmount8(), 0.01);
        
        // 残高は全額
        assertEquals(550.0, summary.getRemainingAmount10(), 0.01);
        assertEquals(324.0, summary.getRemainingAmount8(), 0.01);
        assertEquals(874.0, summary.getRemainingTotal(), 0.01);
    }

    @Test
    @DisplayName("全額モードで領収書が正しく発行される")
    void testIssueReceipt_FullMode() {
        // Arrange
        List<PaymentDetail> details = new ArrayList<>();
        
        Menu menu1 = new Menu();
        menu1.setPrice(500.0);
        PaymentDetail detail1 = new PaymentDetail();
        detail1.setMenu(menu1);
        detail1.setQuantity(1);
        detail1.setTaxRate(taxRate10);
        detail1.setSubtotal(500.0);
        detail1.setDiscount(0.0);
        
        details.add(detail1);

        when(paymentRepository.findById(1)).thenReturn(Optional.of(testPayment));
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        when(paymentDetailRepository.findByPaymentPaymentId(1)).thenReturn(details);
        when(receiptRepository.findByPaymentPaymentIdAndVoidedFalseOrderByIssuedAtDesc(1)).thenReturn(new ArrayList<>());
        when(receiptRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(receiptRepository.save(any(Receipt.class))).thenAnswer(invocation -> {
            Receipt receipt = invocation.getArgument(0);
            receipt.setReceiptId(1);
            return receipt;
        });

        ReceiptIssueRequest request = new ReceiptIssueRequest();
        request.setPaymentId(1);
        request.setUserId(1);
        request.setMode("FULL");
        request.setIdempotencyKey("test-key-123");

        // Act
        Receipt receipt = receiptService.issueReceipt(request);

        // Assert
        assertNotNull(receipt);
        assertNotNull(receipt.getReceiptNo());
        
        // 税込550円の領収書
        // 税抜: 550/1.1 = 500円、税額: 50円
        assertEquals(500.0, receipt.getNetAmount10(), 0.01);
        assertEquals(50.0, receipt.getTaxAmount10(), 0.01);
        assertEquals(0.0, receipt.getNetAmount8(), 0.01);
        assertEquals(0.0, receipt.getTaxAmount8(), 0.01);
    }

    @Test
    @DisplayName("金額指定モードで按分計算が正しく行われる")
    void testIssueReceipt_AmountMode_Apportionment() {
        // Arrange
        List<PaymentDetail> details = new ArrayList<>();
        
        // 10%の商品: 税込550円
        Menu menu1 = new Menu();
        menu1.setPrice(500.0);
        PaymentDetail detail1 = new PaymentDetail();
        detail1.setMenu(menu1);
        detail1.setQuantity(1);
        detail1.setTaxRate(taxRate10);
        detail1.setSubtotal(500.0);
        detail1.setDiscount(0.0);
        
        // 8%の商品: 税込324円
        Menu menu2 = new Menu();
        menu2.setPrice(300.0);
        PaymentDetail detail2 = new PaymentDetail();
        detail2.setMenu(menu2);
        detail2.setQuantity(1);
        detail2.setTaxRate(taxRate8);
        detail2.setSubtotal(300.0);
        detail2.setDiscount(0.0);
        
        details.add(detail1);
        details.add(detail2);

        testPayment.setSubtotal(800.0);
        testPayment.setTotal(874.0); // 550 + 324

        when(paymentRepository.findById(1)).thenReturn(Optional.of(testPayment));
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        when(paymentDetailRepository.findByPaymentPaymentId(1)).thenReturn(details);
        when(receiptRepository.findByPaymentPaymentIdAndVoidedFalseOrderByIssuedAtDesc(1)).thenReturn(new ArrayList<>());
        when(receiptRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(receiptRepository.save(any(Receipt.class))).thenAnswer(invocation -> {
            Receipt receipt = invocation.getArgument(0);
            receipt.setReceiptId(1);
            return receipt;
        });

        // 400円分を発行（全体874円のうち）
        ReceiptIssueRequest request = new ReceiptIssueRequest();
        request.setPaymentId(1);
        request.setUserId(1);
        request.setMode("AMOUNT");
        request.setAmount(400.0);
        request.setIdempotencyKey("test-key-456");

        // Act
        Receipt receipt = receiptService.issueReceipt(request);

        // Assert
        assertNotNull(receipt);
        
        // 按分計算: 
        // 残高: 10%=550円, 8%=324円, 合計=874円
        // 比率: 10%=550/874≒0.629, 8%=324/874≒0.371
        // 400円を按分: 10%=400*0.629≒252円, 8%=400-252=148円
        
        BigDecimal amount10 = BigDecimal.valueOf(400).multiply(
            BigDecimal.valueOf(550).divide(BigDecimal.valueOf(874), 10, RoundingMode.HALF_UP)
        ).setScale(0, RoundingMode.HALF_UP);
        
        BigDecimal amount8 = BigDecimal.valueOf(400).subtract(amount10);
        
        // 税抜・税額を計算
        BigDecimal net10 = amount10.divide(BigDecimal.valueOf(1.10), 0, RoundingMode.HALF_UP);
        BigDecimal tax10 = amount10.subtract(net10);
        BigDecimal net8 = amount8.divide(BigDecimal.valueOf(1.08), 0, RoundingMode.HALF_UP);
        BigDecimal tax8 = amount8.subtract(net8);
        
        assertEquals(net10.doubleValue(), receipt.getNetAmount10(), 0.01);
        assertEquals(tax10.doubleValue(), receipt.getTaxAmount10(), 0.01);
        assertEquals(net8.doubleValue(), receipt.getNetAmount8(), 0.01);
        assertEquals(tax8.doubleValue(), receipt.getTaxAmount8(), 0.01);
    }

    @Test
    @DisplayName("二重発行防止が正しく動作する")
    void testIssueReceipt_IdempotencyKey() {
        // Arrange
        Receipt existingReceipt = new Receipt();
        existingReceipt.setReceiptId(999);
        existingReceipt.setIdempotencyKey("duplicate-key");

        when(receiptRepository.findByIdempotencyKey("duplicate-key")).thenReturn(Optional.of(existingReceipt));

        ReceiptIssueRequest request = new ReceiptIssueRequest();
        request.setPaymentId(1);
        request.setUserId(1);
        request.setMode("FULL");
        request.setIdempotencyKey("duplicate-key");

        // Act
        Receipt receipt = receiptService.issueReceipt(request);

        // Assert
        assertEquals(999, receipt.getReceiptId());
        verify(receiptRepository, never()).save(any());
    }

    @Test
    @DisplayName("発行額が残高を超える場合は例外が発生する")
    void testIssueReceipt_AmountExceedsRemaining() {
        // Arrange
        List<PaymentDetail> details = new ArrayList<>();
        
        Menu menu1 = new Menu();
        menu1.setPrice(500.0);
        PaymentDetail detail1 = new PaymentDetail();
        detail1.setMenu(menu1);
        detail1.setQuantity(1);
        detail1.setTaxRate(taxRate10);
        detail1.setSubtotal(500.0);
        detail1.setDiscount(0.0);
        
        details.add(detail1);

        when(paymentRepository.findById(1)).thenReturn(Optional.of(testPayment));
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        when(paymentDetailRepository.findByPaymentPaymentId(1)).thenReturn(details);
        when(receiptRepository.findByPaymentPaymentIdAndVoidedFalseOrderByIssuedAtDesc(1)).thenReturn(new ArrayList<>());
        when(receiptRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());

        ReceiptIssueRequest request = new ReceiptIssueRequest();
        request.setPaymentId(1);
        request.setUserId(1);
        request.setMode("AMOUNT");
        request.setAmount(1000.0); // 残高550円を超える
        request.setIdempotencyKey("test-key-789");

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            receiptService.issueReceipt(request);
        });
    }
}
