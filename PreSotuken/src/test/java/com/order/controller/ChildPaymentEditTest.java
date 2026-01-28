package com.order.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import com.order.dto.ChildPaymentUpdateRequest;
import com.order.entity.*;
import com.order.repository.*;
import com.order.service.PaymentSplitService;

/**
 * 子会計（割り勘詳細）の編集機能のテスト
 */
@ExtendWith(MockitoExtension.class)
class ChildPaymentEditTest {

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
    
    private Store store;
    private Visit visit;
    private Payment parentPayment;
    private Payment childPayment;
    private PaymentType paymentType1;
    private PaymentType paymentType2;
    
    @BeforeEach
    void setUp() {
        store = new Store();
        store.setStoreId(1);
        
        visit = new Visit();
        visit.setVisitId(1);
        
        parentPayment = new Payment();
        parentPayment.setPaymentId(100);
        parentPayment.setStore(store);
        parentPayment.setVisit(visit);
        parentPayment.setTotal(3000.0);
        parentPayment.setTotalSplits(3);
        parentPayment.setPaymentStatus("PARTIAL");
        
        paymentType1 = new PaymentType();
        paymentType1.setTypeId(1);
        paymentType1.setTypeName("現金");
        
        paymentType2 = new PaymentType();
        paymentType2.setTypeId(2);
        paymentType2.setTypeName("クレジットカード");
        
        childPayment = new Payment();
        childPayment.setPaymentId(101);
        childPayment.setStore(store);
        childPayment.setVisit(visit);
        childPayment.setParentPayment(parentPayment);
        childPayment.setSplitNumber(1);
        childPayment.setTotal(1000.0);
        childPayment.setPaymentType(paymentType1);
        childPayment.setPaymentTime(LocalDateTime.now());
    }
    
    @Test
    void testEditChildPayment_正常系_支払い方法と金額を更新() {
        // 準備
        when(paymentRepository.findById(100)).thenReturn(Optional.of(parentPayment));
        when(paymentRepository.findById(101)).thenReturn(Optional.of(childPayment));
        when(paymentTypeRepository.findById(2)).thenReturn(Optional.of(paymentType2));
        when(paymentRepository.save(any(Payment.class))).thenReturn(childPayment);
        
        ChildPaymentUpdateRequest request = new ChildPaymentUpdateRequest();
        request.setPaymentTypeId(2);
        request.setAmount(1200.0);
        
        // 実行
        ResponseEntity<?> response = paymentController.editChildPayment(1, 100, 101, request);
        
        // 検証
        assertEquals(200, response.getStatusCode().value());
        verify(paymentRepository, times(1)).save(any(Payment.class));
        assertEquals(paymentType2, childPayment.getPaymentType());
        assertEquals(1200.0, childPayment.getTotal());
    }
    
    @Test
    void testEditChildPayment_正常系_支払い方法のみ更新() {
        // 準備
        when(paymentRepository.findById(100)).thenReturn(Optional.of(parentPayment));
        when(paymentRepository.findById(101)).thenReturn(Optional.of(childPayment));
        when(paymentTypeRepository.findById(2)).thenReturn(Optional.of(paymentType2));
        when(paymentRepository.save(any(Payment.class))).thenReturn(childPayment);
        
        ChildPaymentUpdateRequest request = new ChildPaymentUpdateRequest();
        request.setPaymentTypeId(2);
        
        // 実行
        ResponseEntity<?> response = paymentController.editChildPayment(1, 100, 101, request);
        
        // 検証
        assertEquals(200, response.getStatusCode().value());
        verify(paymentRepository, times(1)).save(any(Payment.class));
        assertEquals(paymentType2, childPayment.getPaymentType());
        assertEquals(1000.0, childPayment.getTotal()); // 金額は変更されていない
    }
    
    @Test
    void testEditChildPayment_正常系_金額のみ更新() {
        // 準備
        when(paymentRepository.findById(100)).thenReturn(Optional.of(parentPayment));
        when(paymentRepository.findById(101)).thenReturn(Optional.of(childPayment));
        when(paymentRepository.save(any(Payment.class))).thenReturn(childPayment);
        
        ChildPaymentUpdateRequest request = new ChildPaymentUpdateRequest();
        request.setAmount(1500.0);
        
        // 実行
        ResponseEntity<?> response = paymentController.editChildPayment(1, 100, 101, request);
        
        // 検証
        assertEquals(200, response.getStatusCode().value());
        verify(paymentRepository, times(1)).save(any(Payment.class));
        assertEquals(paymentType1, childPayment.getPaymentType()); // 支払い方法は変更されていない
        assertEquals(1500.0, childPayment.getTotal());
    }
    
    @Test
    void testEditChildPayment_異常系_親会計が見つからない() {
        // 準備
        when(paymentRepository.findById(100)).thenReturn(Optional.empty());
        
        ChildPaymentUpdateRequest request = new ChildPaymentUpdateRequest();
        request.setAmount(1200.0);
        
        // 実行
        ResponseEntity<?> response = paymentController.editChildPayment(1, 100, 101, request);
        
        // 検証
        assertEquals(404, response.getStatusCode().value());
        verify(paymentRepository, never()).save(any(Payment.class));
    }
    
    @Test
    void testEditChildPayment_異常系_子会計が見つからない() {
        // 準備
        when(paymentRepository.findById(100)).thenReturn(Optional.of(parentPayment));
        when(paymentRepository.findById(101)).thenReturn(Optional.empty());
        
        ChildPaymentUpdateRequest request = new ChildPaymentUpdateRequest();
        request.setAmount(1200.0);
        
        // 実行
        ResponseEntity<?> response = paymentController.editChildPayment(1, 100, 101, request);
        
        // 検証
        assertEquals(404, response.getStatusCode().value());
        verify(paymentRepository, never()).save(any(Payment.class));
    }
    
    @Test
    void testEditChildPayment_異常系_子会計が親会計に属していない() {
        // 準備
        Payment anotherParent = new Payment();
        anotherParent.setPaymentId(200);
        anotherParent.setStore(store);
        
        childPayment.setParentPayment(anotherParent);
        
        when(paymentRepository.findById(100)).thenReturn(Optional.of(parentPayment));
        when(paymentRepository.findById(101)).thenReturn(Optional.of(childPayment));
        
        ChildPaymentUpdateRequest request = new ChildPaymentUpdateRequest();
        request.setAmount(1200.0);
        
        // 実行
        ResponseEntity<?> response = paymentController.editChildPayment(1, 100, 101, request);
        
        // 検証
        assertEquals(400, response.getStatusCode().value());
        verify(paymentRepository, never()).save(any(Payment.class));
    }
    
    @Test
    void testEditChildPayment_異常系_不正な支払い方法ID() {
        // 準備
        when(paymentRepository.findById(100)).thenReturn(Optional.of(parentPayment));
        when(paymentRepository.findById(101)).thenReturn(Optional.of(childPayment));
        when(paymentTypeRepository.findById(999)).thenReturn(Optional.empty());
        
        ChildPaymentUpdateRequest request = new ChildPaymentUpdateRequest();
        request.setPaymentTypeId(999);
        request.setAmount(1200.0);
        
        // 実行
        ResponseEntity<?> response = paymentController.editChildPayment(1, 100, 101, request);
        
        // 検証
        assertEquals(400, response.getStatusCode().value());
        verify(paymentRepository, never()).save(any(Payment.class));
    }
    
    @Test
    void testEditChildPayment_異常系_金額が0以下() {
        // 準備
        when(paymentRepository.findById(100)).thenReturn(Optional.of(parentPayment));
        when(paymentRepository.findById(101)).thenReturn(Optional.of(childPayment));
        
        ChildPaymentUpdateRequest request = new ChildPaymentUpdateRequest();
        request.setAmount(0.0);
        
        // 実行
        ResponseEntity<?> response = paymentController.editChildPayment(1, 100, 101, request);
        
        // 検証
        assertEquals(400, response.getStatusCode().value());
        verify(paymentRepository, never()).save(any(Payment.class));
    }
    
    @Test
    void testEditChildPayment_異常系_店舗IDが一致しない() {
        // 準備
        Store anotherStore = new Store();
        anotherStore.setStoreId(2);
        parentPayment.setStore(anotherStore);
        
        when(paymentRepository.findById(100)).thenReturn(Optional.of(parentPayment));
        
        ChildPaymentUpdateRequest request = new ChildPaymentUpdateRequest();
        request.setAmount(1200.0);
        
        // 実行
        ResponseEntity<?> response = paymentController.editChildPayment(1, 100, 101, request);
        
        // 検証
        assertEquals(404, response.getStatusCode().value());
        verify(paymentRepository, never()).save(any(Payment.class));
    }
    
    @Test
    void testEditChildPayment_異常系_両方のフィールドがnull() {
        // 準備
        ChildPaymentUpdateRequest request = new ChildPaymentUpdateRequest();
        // paymentTypeIdもamountも設定しない
        
        // 実行
        ResponseEntity<?> response = paymentController.editChildPayment(1, 100, 101, request);
        
        // 検証
        assertEquals(400, response.getStatusCode().value());
        verify(paymentRepository, never()).save(any(Payment.class));
    }
    
    @Test
    void testEditChildPayment_異常系_storeIdがnull() {
        // 準備
        ChildPaymentUpdateRequest request = new ChildPaymentUpdateRequest();
        request.setAmount(1200.0);
        
        // 実行 (storeIdをnullで渡す)
        ResponseEntity<?> response = paymentController.editChildPayment(null, 100, 101, request);
        
        // 検証
        assertEquals(401, response.getStatusCode().value());
        verify(paymentRepository, never()).save(any(Payment.class));
    }
}
