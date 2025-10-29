package com.order.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.order.dto.IndividualPaymentRequest;
import com.order.dto.SplitPaymentRequest;
import com.order.entity.*;
import com.order.repository.*;

/**
 * 会計方法の混在を防ぐバックエンド検証のテスト
 */
@ExtendWith(MockitoExtension.class)
class PaymentMethodMixingPreventionTest {

    @Mock
    private PaymentRepository paymentRepository;
    
    @Mock
    private PaymentDetailRepository paymentDetailRepository;
    
    @Mock
    private PaymentTypeRepository paymentTypeRepository;
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private VisitRepository visitRepository;
    
    @InjectMocks
    private PaymentSplitService paymentSplitService;
    
    private Store store;
    private Payment payment;
    private PaymentDetail paymentDetail;
    
    @BeforeEach
    void setUp() {
        store = new Store();
        store.setStoreId(1);
        
        Seat seat = new Seat();
        seat.setSeatId(1);
        seat.setStore(store);
        
        Visit visit = new Visit();
        visit.setVisitId(1);
        visit.setSeat(seat);
        
        payment = new Payment();
        payment.setPaymentId(1);
        payment.setStore(store);
        payment.setVisit(visit);
        payment.setTotal(1000.0);
        
        Menu menu = new Menu();
        menu.setMenuId(1);
        menu.setMenuName("テストメニュー");
        menu.setPrice(500.0);
        
        TaxRate taxRate = new TaxRate();
        taxRate.setTaxRateId(1);
        taxRate.setRate(0.1);
        
        paymentDetail = new PaymentDetail();
        paymentDetail.setPaymentDetailId(1);
        paymentDetail.setPayment(payment);
        paymentDetail.setMenu(menu);
        paymentDetail.setQuantity(2);
        paymentDetail.setSubtotal(1000.0);
        paymentDetail.setTaxRate(taxRate);
    }
    
    /**
     * 個別会計開始後、割り勘会計を実行しようとするとエラーになることを確認
     */
    @Test
    void testSplitPayment_FailsWhenIndividualPaymentInProgress() {
        // 準備: 個別会計が進行中の状態（PARTIAL、totalSplitsがnull）
        payment.setPaymentStatus("PARTIAL");
        payment.setTotalSplits(null);
        
        when(paymentRepository.findById(1)).thenReturn(Optional.of(payment));
        
        SplitPaymentRequest request = new SplitPaymentRequest();
        request.setPaymentId(1);
        request.setNumberOfSplits(3);
        request.setCurrentSplit(1);
        request.setPaymentTime(LocalDateTime.now());
        
        // 実行と検証
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> paymentSplitService.processSplitPayment(request)
        );
        
        assertTrue(exception.getMessage().contains("個別会計が進行中のため、割り勘会計を開始できません"));
    }
    
    /**
     * 個別会計開始後、totalSplitsが0の場合も割り勘会計を実行しようとするとエラーになることを確認
     */
    @Test
    void testSplitPayment_FailsWhenIndividualPaymentInProgress_WithZeroTotalSplits() {
        // 準備: 個別会計が進行中の状態（PARTIAL、totalSplits = 0）
        payment.setPaymentStatus("PARTIAL");
        payment.setTotalSplits(0);
        
        when(paymentRepository.findById(1)).thenReturn(Optional.of(payment));
        
        SplitPaymentRequest request = new SplitPaymentRequest();
        request.setPaymentId(1);
        request.setNumberOfSplits(3);
        request.setCurrentSplit(1);
        request.setPaymentTime(LocalDateTime.now());
        
        // 実行と検証
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> paymentSplitService.processSplitPayment(request)
        );
        
        assertTrue(exception.getMessage().contains("個別会計が進行中のため、割り勘会計を開始できません"));
    }
    
    /**
     * 割り勘会計開始後、個別会計を実行しようとするとエラーになることを確認
     */
    @Test
    void testIndividualPayment_FailsWhenSplitPaymentInProgress() {
        // 準備: 割り勘会計が進行中の状態（PARTIAL、totalSplits > 0）
        payment.setPaymentStatus("PARTIAL");
        payment.setTotalSplits(3);
        
        when(paymentRepository.findById(1)).thenReturn(Optional.of(payment));
        
        IndividualPaymentRequest request = new IndividualPaymentRequest();
        request.setPaymentId(1);
        request.setPaymentTime(LocalDateTime.now());
        
        // 実行と検証
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> paymentSplitService.processIndividualPayment(request)
        );
        
        assertTrue(exception.getMessage().contains("割り勘会計が進行中のため、個別会計を開始できません"));
    }
    
    /**
     * 通常状態（PARTIAL以外）では、割り勘会計を開始できることを確認
     */
    @Test
    void testSplitPayment_SucceedsWhenNoOtherPaymentInProgress() {
        // 準備: 通常の状態
        payment.setPaymentStatus("COMPLETED"); // または null
        payment.setTotalSplits(null);
        
        when(paymentRepository.findById(1)).thenReturn(Optional.of(payment));
        when(paymentDetailRepository.findByPaymentPaymentId(1)).thenReturn(Arrays.asList(paymentDetail));
        when(paymentRepository.findByParentPaymentPaymentId(1)).thenReturn(Collections.emptyList());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        SplitPaymentRequest request = new SplitPaymentRequest();
        request.setPaymentId(1);
        request.setNumberOfSplits(3);
        request.setCurrentSplit(1);
        request.setPaymentTime(LocalDateTime.now());
        request.setDeposit(1000.0);
        
        // 実行
        Payment result = paymentSplitService.processSplitPayment(request);
        
        // 検証
        assertNotNull(result);
        assertEquals("PARTIAL", result.getPaymentStatus());
    }
    
    /**
     * 通常状態（PARTIAL以外）では、個別会計を開始できることを確認
     */
    @Test
    void testIndividualPayment_SucceedsWhenNoOtherPaymentInProgress() {
        // 準備: 通常の状態
        payment.setPaymentStatus("COMPLETED"); // または null
        payment.setTotalSplits(null);
        
        when(paymentRepository.findById(1)).thenReturn(Optional.of(payment));
        when(paymentDetailRepository.findById(1)).thenReturn(Optional.of(paymentDetail));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentDetailRepository.save(any(PaymentDetail.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentDetailRepository.findByPaymentPaymentId(1)).thenReturn(Arrays.asList(paymentDetail));
        
        IndividualPaymentRequest request = new IndividualPaymentRequest();
        request.setPaymentId(1);
        request.setPaymentTime(LocalDateTime.now());
        request.setDeposit(1000.0);
        
        IndividualPaymentRequest.ItemSelection item = new IndividualPaymentRequest.ItemSelection();
        item.setPaymentDetailId(1);
        item.setQuantity(1);
        request.setItems(Arrays.asList(item));
        
        // 実行
        Payment result = paymentSplitService.processIndividualPayment(request);
        
        // 検証
        assertNotNull(result);
        assertEquals("PARTIAL", result.getPaymentStatus());
    }
}
