package com.order.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.Model;

import com.order.entity.*;
import com.order.repository.*;
import com.order.service.PaymentSplitService;

/**
 * 会計ボタンの状態制御機能のテスト
 * 個別会計・割り勘会計開始後、他の会計方法ボタンが適切に制御されることを確認
 */
@ExtendWith(MockitoExtension.class)
class PaymentButtonStateTest {

    @Mock
    private VisitRepository visitRepository;
    
    @Mock
    private PaymentRepository paymentRepository;
    
    @Mock
    private PaymentDetailRepository paymentDetailRepository;
    
    @Mock
    private PaymentTypeRepository paymentTypeRepository;
    
    @Mock
    private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private SeatRepository seatRepository;
    
    @Mock
    private PaymentSplitService paymentSplitService;
    
    @InjectMocks
    private PaymentController paymentController;
    
    @Mock
    private Model model;
    
    private Store store;
    private Seat seat;
    private Visit visit;
    private Payment payment;
    private PaymentDetail paymentDetail;
    private User user;
    private PaymentType paymentType;
    
    @BeforeEach
    void setUp() {
        // テストデータの準備
        store = new Store();
        store.setStoreId(1);
        store.setStoreName("テスト店舗");
        
        seat = new Seat();
        seat.setSeatId(1);
        seat.setSeatName("テーブル1");
        seat.setStore(store);
        
        visit = new Visit();
        visit.setVisitId(1);
        visit.setSeat(seat);
        visit.setNumberOfPeople(2);
        
        payment = new Payment();
        payment.setPaymentId(1);
        payment.setStore(store);
        payment.setVisit(visit);
        payment.setSubtotal(1000.0);
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
        
        user = new User();
        user.setUserId(1);
        user.setUserName("テスト担当者");
        
        paymentType = new PaymentType();
        paymentType.setTypeId(1);
        paymentType.setTypeName("現金");
    }
    
    /**
     * 通常の会計画面表示時、paymentStatusがCOMPLETEDの場合のテスト
     */
    @Test
    void testShowPaymentDetail_WithCompletedStatus() {
        // 準備
        payment.setPaymentStatus("COMPLETED");
        
        when(visitRepository.findById(1)).thenReturn(Optional.of(visit));
        when(paymentRepository.findByVisitVisitIdAndParentPaymentIsNull(1)).thenReturn(payment);
        when(paymentDetailRepository.findByPaymentPaymentId(1)).thenReturn(Arrays.asList(paymentDetail));
        when(paymentTypeRepository.findByStoreId(1)).thenReturn(Arrays.asList(paymentType));
        when(userRepository.findByStore_StoreId(1)).thenReturn(Arrays.asList(user));
        
        // 実行
        String viewName = paymentController.showPaymentDetail(1, 1, model);
        
        // 検証
        assertEquals("payment", viewName);
        verify(model).addAttribute(eq("payment"), any(Payment.class));
        
        // paymentオブジェクトがモデルに追加されていることを確認
        verify(model).addAttribute(eq("payment"), argThat(p -> 
            p instanceof Payment && "COMPLETED".equals(((Payment) p).getPaymentStatus())
        ));
    }
    
    /**
     * 割り勘会計開始後、paymentStatusがPARTIALになることを確認
     */
    @Test
    void testShowPaymentDetail_WithPartialStatus_AfterSplitPayment() {
        // 準備
        payment.setPaymentStatus("PARTIAL");
        payment.setTotalSplits(3); // 3人で割り勘
        
        when(visitRepository.findById(1)).thenReturn(Optional.of(visit));
        when(paymentRepository.findByVisitVisitIdAndParentPaymentIsNull(1)).thenReturn(payment);
        when(paymentDetailRepository.findByPaymentPaymentId(1)).thenReturn(Arrays.asList(paymentDetail));
        when(paymentTypeRepository.findByStoreId(1)).thenReturn(Arrays.asList(paymentType));
        when(userRepository.findByStore_StoreId(1)).thenReturn(Arrays.asList(user));
        
        // 実行
        String viewName = paymentController.showPaymentDetail(1, 1, model);
        
        // 検証
        assertEquals("payment", viewName);
        
        // paymentStatusがPARTIALであることを確認
        verify(model).addAttribute(eq("payment"), argThat(p -> 
            p instanceof Payment && "PARTIAL".equals(((Payment) p).getPaymentStatus())
        ));
    }
    
    /**
     * 個別会計開始後、paymentStatusがPARTIALになることを確認
     */
    @Test
    void testShowPaymentDetail_WithPartialStatus_AfterIndividualPayment() {
        // 準備
        payment.setPaymentStatus("PARTIAL");
        payment.setTotalSplits(null); // 個別会計の場合はnull
        
        when(visitRepository.findById(1)).thenReturn(Optional.of(visit));
        when(paymentRepository.findByVisitVisitIdAndParentPaymentIsNull(1)).thenReturn(payment);
        when(paymentDetailRepository.findByPaymentPaymentId(1)).thenReturn(Arrays.asList(paymentDetail));
        when(paymentTypeRepository.findByStoreId(1)).thenReturn(Arrays.asList(paymentType));
        when(userRepository.findByStore_StoreId(1)).thenReturn(Arrays.asList(user));
        
        // 実行
        String viewName = paymentController.showPaymentDetail(1, 1, model);
        
        // 検証
        assertEquals("payment", viewName);
        
        // paymentStatusがPARTIALであることを確認
        verify(model).addAttribute(eq("payment"), argThat(p -> 
            p instanceof Payment && "PARTIAL".equals(((Payment) p).getPaymentStatus())
        ));
    }
}
